/*
 * |-------------------------------------------------
 * | Copyright © 2017 Colin But. All rights reserved.
 * |-------------------------------------------------
 */
package com.mycompany.entapp.snowman.infrastructure.db.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public abstract class AbstractJDBCDao {

    private static final String DEFAULT_DATABASE_CONNECTION_URL =
        "jdbc:mysql://localhost:3306/snowman?createDatabaseIfNotExist=true";
    private static final String DEFAULT_DATABASE_USERNAME = "username";
    private static final String DEFAULT_DATABASE_PASSWORD = "password";
    private static final String DEFAULT_DATABASE_DRIVER = "com.mysql.cj.jdbc.Driver";

    protected void setupDBDriver() throws SQLException {
        String driver = System.getProperty("jdbc.driverClassName", DEFAULT_DATABASE_DRIVER);
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Unable to load JDBC driver " + driver, e);
        }
    }

    protected Connection getConnection() throws SQLException {
        String url = System.getProperty("jdbc.url", DEFAULT_DATABASE_CONNECTION_URL);
        String username = System.getProperty("jdbc.username", DEFAULT_DATABASE_USERNAME);
        String password = System.getProperty("jdbc.password", DEFAULT_DATABASE_PASSWORD);
        return DriverManager.getConnection(url, username, password);
    }
}
