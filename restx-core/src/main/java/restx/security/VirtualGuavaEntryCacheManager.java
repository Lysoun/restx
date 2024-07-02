package restx.security;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import restx.security.RestxSession.Definition.CachedEntry;
import restx.security.RestxSession.Definition.Entry;
import restx.security.RestxSession.Definition.EntryCacheManager;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * A restx session entry cache manager based on guava cache.
 *
 * You can override the cache settings by overriding the getCacheBuilder() method.
 *
 * Note that Guava Cache is not distributed, so be very careful with cache invalidation
 * when using this cache.
 *
 * getValue is rewrite for avoid synchronized block.
 */
public class VirtualGuavaEntryCacheManager implements EntryCacheManager {
    @Override
    public <T> CachedEntry<T> getCachedEntry(Entry<T> entry) {
         return new VirtualGuavaCacheSessionDefinitionEntry<>(
                entry.getKey(),
                getCache(entry),
                (key -> entry.getValueForId(key).orNull()),
                getLockMaxTimeToWait(entry)
        );
    }

    protected <T> Cache<String, T> getCache(final Entry<T> entry) {
        return getCacheBuilder(entry).build();
    }

    protected <T> Duration getLockMaxTimeToWait(Entry<T> entry) {
        return Duration.ofSeconds(1);
    }

    protected <T> CacheBuilder<Object, Object> getCacheBuilder(Entry<T> entry) {
        return CacheBuilder.newBuilder().maximumSize(1000);
    }

    /**
     * A session definition entry implementation using Guava Cache.
     */
    public static class VirtualGuavaCacheSessionDefinitionEntry<T> implements CachedEntry<T> {
        private final Cache<String, T> cache;
        private final String key;
        private final Function<String, T> mappingFunction;
        private final Long lockMaxMillisToWait;

        // Using ConcurrentHashMap for generate lock.
        // Lock are used over computeIfAbsent because this method use a synchronized block
        private final ConcurrentHashMap<String, Lock> locks = new ConcurrentHashMap<>();

        // Use for clear values in cache and locks
        private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        public VirtualGuavaCacheSessionDefinitionEntry(
                String key,
                Cache<String, T> cache,
                Function<String, T> mappingFunction,
                Duration lockMaxTimeToWait
        ) {
            this.key = key;
            this.cache = cache;
            this.mappingFunction = mappingFunction;
            this.lockMaxMillisToWait = lockMaxTimeToWait.toMillis();
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Optional<T> getValueForId(String valueId) {
            final Lock readLock = readWriteLock.readLock();
            acquireLockOrFail(readLock);

            try {
                T value = cache.getIfPresent(valueId);

                if (value == null) {
                    value = computeValueForId(valueId);
                }

                return Optional.fromNullable(value);
            } catch (RuntimeException e) {
                throw new RuntimeException("impossible to load object from cache using valueid " + valueId +
                        " for " + key + ": " + e.getMessage(), e);
            } finally {
                readLock.unlock();
            }
        }

        @Override
        public void invalidateCacheFor(String valueId) {
            final Lock writeLock = readWriteLock.writeLock();

            acquireLockOrFail(writeLock);
            try {
                cache.invalidate(valueId);
                locks.remove(valueId);
            } catch (RuntimeException e) {
                throw new RuntimeException("impossible to acquire write lock for " + key + " and remove" + valueId + ": " + e.getMessage(), e);
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void invalidateCache() {
            final Lock writeLock = readWriteLock.writeLock();

            acquireLockOrFail(writeLock);
            try {
                cache.invalidateAll();
                locks.clear();
            } catch (RuntimeException e) {
                throw new RuntimeException("impossible to acquire write lock for " + key + " and clear cache: " + e.getMessage(), e);
            } finally {
                writeLock.unlock();
            }
        }

        private T computeValueForId(final String valueId) {
            T value;

            Lock valueLock = locks.computeIfAbsent(valueId, k -> new ReentrantLock());
            acquireLockOrFail(valueLock);
            try {
                // check if value was already compute by another thread
                value = cache.getIfPresent(valueId);

                if (value == null) {
                    // compute value
                    value = this.mappingFunction.apply(valueId);

                    if (value != null) {
                        cache.put(key, value);
                    }
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("impossible to load object from cache using valueid " + valueId +
                        " for " + key + ": " + e.getMessage(), e);
            } finally {
                valueLock.unlock();
                locks.remove(valueId);
            }

            return value;
        }

        private void acquireLockOrFail(Lock lock) {
            try {
                if (!lock.tryLock(this.lockMaxMillisToWait, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Cannot acquire lock in " + lockMaxMillisToWait + " milliseconds");
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException("Cannot acquire lock", e);
            }
        }
    }
}
