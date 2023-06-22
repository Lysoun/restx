/*
 * Copyright (C) 2011 Benoit GUEROUT <bguerout at gmail dot com> and Yves AMSELLEM <amsellem dot yves at gmail dot com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package restx.jongo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import com.fasterxml.jackson.databind.introspect.BasicClassIntrospector;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.jongo.ObjectIdUpdater;
import org.jongo.marshall.jackson.IdSelector;
import org.bson.types.ObjectId;

// see org.jongo.marshall.jackson.JacksonObjectIdUpdater
public class BackwardCompatibleJacksonObjectIdUpdater implements ObjectIdUpdater {

    private final ObjectMapper mapper;
    private final IdSelector<BeanPropertyDefinition> idSelector;

    public BackwardCompatibleJacksonObjectIdUpdater(ObjectMapper mapper) {
        this(mapper, new org.jongo.marshall.jackson.JacksonObjectIdUpdater.BeanPropertyDefinitionIdSelector());
    }

    public BackwardCompatibleJacksonObjectIdUpdater(ObjectMapper mapper, IdSelector<BeanPropertyDefinition> idSelector) {
        this.mapper = mapper;
        this.idSelector = idSelector;
    }

    public boolean mustGenerateObjectId(Object pojo) {
        for (BeanPropertyDefinition def : beanDescription(pojo.getClass()).findProperties()) {
            if (idSelector.isId(def)) {
                AnnotatedMember accessor = def.getAccessor();
                accessor.fixAccess(true);
                return (ObjectId.class.isAssignableFrom(accessor.getRawType())
                        || String.class.isAssignableFrom(accessor.getRawType()))
                        && accessor.getValue(pojo) == null;
            }
        }
        return false;
    }

    public Object getId(Object pojo) {
        BasicBeanDescription beanDescription = beanDescription(pojo.getClass());
        for (BeanPropertyDefinition def : beanDescription.findProperties()) {
            if (idSelector.isId(def)) {
                AnnotatedMember accessor = def.getAccessor();
                accessor.fixAccess(true);
                Object id = accessor.getValue(pojo);
                if (id instanceof String && idSelector.isObjectId(def)) {
                    return new ObjectId(id.toString());
                } else {
                    return id;
                }
            }
        }
        return null;
    }

    public void setObjectId(Object target, ObjectId id) {
        for (BeanPropertyDefinition def : beanDescription(target.getClass()).findProperties()) {
            if (idSelector.isId(def)) {
                AnnotatedMember accessor = def.getAccessor();
                accessor.fixAccess(true);
                if (accessor.getValue(target) != null) {
                    throw new IllegalArgumentException("Unable to set objectid on class: " + target.getClass());
                }
                AnnotatedMember field = def.getField();
                field.fixAccess(true);
                Class<?> type = field.getRawType();
                if (ObjectId.class.isAssignableFrom(type)) {
                    field.setValue(target, id);
                } else if (type.equals(String.class)) {
                    field.setValue(target, id.toString());
                }
                return;
            }
        }
    }

    private BasicBeanDescription beanDescription(Class<?> cls) {
        BasicClassIntrospector bci = new BasicClassIntrospector();
        return bci.forSerialization(mapper.getSerializationConfig(), mapper.constructType(cls), mapper.getSerializationConfig());
    }
}