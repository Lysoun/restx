package restx.server;

import com.google.common.base.Throwables;
import jakarta.servlet.ServletException;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * TomcatWebServer allow to run embedded tomcat. But its startup time is much slower than JettyWebServer.
 */
public class TomcatWebServer extends WebServerBase {
    private final static Logger logger = LoggerFactory.getLogger(TomcatWebServer.class);

    private final Tomcat tomcat;
    private final Context context;

    public TomcatWebServer(String appBase, int port, String bindInterface, boolean virtualThread) throws ServletException {
        super(checkNotNull(appBase), port, bindInterface, "Apache Tomcat", "org.apache.tomcat", "tomcat-catalina");

        tomcat = new Tomcat();

        tomcat.setBaseDir(".");
        tomcat.getHost().setAppBase(".");

        if (virtualThread) {
            // Set a custom connector with VirtualThreadExecutor
            Connector connector = new Connector();
            connector.setPort(port);
            connector.getProtocolHandler().setExecutor(new VirtualThreadExecutor("http"));
            tomcat.setConnector(connector);
        } else {
            tomcat.setPort(port);

            // Create default connector with port
            // Do not remove as Tomcat open the port only if getConnector is called at least once
            tomcat.getConnector();
        }

        String contextPath = "";

        // Add AprLifecycleListener
        StandardServer server = (StandardServer) tomcat.getServer();
        AprLifecycleListener listener = new AprLifecycleListener();
        server.addLifecycleListener(listener);

        context = tomcat.addWebapp(contextPath, appBase);
    }

    @Override
    protected void _start() throws LifecycleException {
        context.addParameter("restx.baseServerUri", baseUrl());
        context.addParameter("restx.serverId", serverId);

        tomcat.start();
    }

    @Override
    public void await() {
        tomcat.getServer().await();
    }

    protected void _stop() throws LifecycleException {
        tomcat.stop();
    }

    public static WebServerSupplier tomcatWebServerSupplier(final String appBase, final String bindInterface) {
        return port -> {
            try {
                return new TomcatWebServer(appBase, port, bindInterface, false);
            } catch (ServletException e) {
                throw Throwables.propagate(e);
            }
        };
    }
}
