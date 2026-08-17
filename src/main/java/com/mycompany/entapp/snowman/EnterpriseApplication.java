/*
 * |-------------------------------------------------
 * | Copyright © 2017 Colin But. All rights reserved.
 * |-------------------------------------------------
 */
package com.mycompany.entapp.snowman;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;

import java.net.URL;

public class EnterpriseApplication {

    private static final int DEFAULT_PORT = 8090;
    private static final String[][] ENVIRONMENT_PROPERTY_MAPPINGS = {
            {"SNOWMAN_JDBC_DRIVER", "jdbc.driverClassName"},
            {"SNOWMAN_JDBC_URL", "jdbc.url"},
            {"SNOWMAN_JDBC_USERNAME", "jdbc.username"},
            {"SNOWMAN_JDBC_PASSWORD", "jdbc.password"},
            {"SNOWMAN_JMS_BROKER_URL", "jms.brokerUrl"},
            {"SNOWMAN_HIBERNATE_DIALECT", "hibernate.dialect"},
            {"SNOWMAN_HIBERNATE_DDL_AUTO", "hibernate.hbm2ddl.auto"},
            {"PORT", "port"}
    };

    private EnterpriseApplication() {
    }

    public static void main(String[] args) throws Exception {

        applyEnvironmentOverrides();

        final Server server = new Server();

        final ServerConnector serverConnector = new ServerConnector(server);

        serverConnector.setPort(resolvePort());

        server.setConnectors(new Connector[]{serverConnector});

        WebAppContext webAppContext = new WebAppContext();
        webAppContext.setDescriptor(getResource("webapp/WEB-INF/web.xml"));
        webAppContext.setResourceBase(getResource("webapp"));
        webAppContext.setContextPath("/");
        webAppContext.setParentLoaderPriority(true);

        server.setHandler(webAppContext);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                if (server.isStarted()) {
                    server.setStopAtShutdown(true);

                    try {
                        server.stop();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }));

        server.join();

    }

    private static String getResource(String resourceName) {
        URL resourceURL = EnterpriseApplication.class.getClassLoader().getResource(resourceName);
        if (resourceURL == null) {
            throw new RuntimeException("Unable to fetch specified resource: " + resourceName);
        }
        return resourceURL.toString();
    }

    private static int resolvePort() {
        try {
            return Integer.parseInt(System.getProperty("port"));
        } catch (NumberFormatException ex) {
            return DEFAULT_PORT;
        }
    }

    private static void applyEnvironmentOverrides() {
        for (String[] mapping : ENVIRONMENT_PROPERTY_MAPPINGS) {
            String environmentValue = System.getenv(mapping[0]);
            if (environmentValue != null
                    && !environmentValue.trim().isEmpty()
                    && System.getProperty(mapping[1]) == null) {
                System.setProperty(mapping[1], environmentValue);
            }
        }
    }
}
