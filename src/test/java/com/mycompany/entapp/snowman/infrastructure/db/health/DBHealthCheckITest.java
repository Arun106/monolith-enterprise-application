package com.mycompany.entapp.snowman.infrastructure.db.health;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DBHealthCheckITest {

    private static final String URL = "jdbc:h2:mem:snowman_health;DB_CLOSE_DELAY=-1";

    @Before
    public void configureDatabase() throws Exception {
        System.setProperty("jdbc.driverClassName", "org.h2.Driver");
        System.setProperty("jdbc.url", URL);
        System.setProperty("jdbc.username", "sa");
        System.setProperty("jdbc.password", "");
        execute("DROP TABLE IF EXISTS app_info");
        execute("CREATE TABLE app_info (id INTEGER PRIMARY KEY, version VARCHAR(32))");
        execute("INSERT INTO app_info (id, version) VALUES (1, 'integration-test')");
    }

    @After
    public void clearDatabaseProperties() throws Exception {
        execute("DROP TABLE IF EXISTS app_info");
        System.clearProperty("jdbc.driverClassName");
        System.clearProperty("jdbc.url");
        System.clearProperty("jdbc.username");
        System.clearProperty("jdbc.password");
    }

    @Test
    public void reportsUpAgainstARealJdbcDatabase() {
        assertTrue(new DBHealthCheck().getDBStatus());
    }

    @Test
    public void reportsDownWhenRequiredTableIsMissing() throws Exception {
        execute("DROP TABLE app_info");

        assertFalse(new DBHealthCheck().getDBStatus());
    }

    private void execute(String sql) throws Exception {
        Connection connection = DriverManager.getConnection(URL, "sa", "");
        Statement statement = connection.createStatement();
        try {
            statement.execute(sql);
        } finally {
            statement.close();
            connection.close();
        }
    }
}
