package com.mycompany.entapp.snowman.infrastructure.db.health;

import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DBHealthCheckUTest {

    @Test
    public void reportsUpWhenDatabaseReturnsARow() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT min(1) from app_info")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        boolean status = new TestableDBHealthCheck(connection).getDBStatus();

        assertTrue(status);
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    public void reportsDownWhenDatabaseReturnsNoRows() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT min(1) from app_info")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        assertFalse(new TestableDBHealthCheck(connection).getDBStatus());
    }

    @Test
    public void reportsDownWhenConnectionFails() {
        assertFalse(new TestableDBHealthCheck(new SQLException("offline")).getDBStatus());
    }

    private static class TestableDBHealthCheck extends DBHealthCheck {
        private final Connection connection;
        private final SQLException failure;

        TestableDBHealthCheck(Connection connection) {
            this.connection = connection;
            this.failure = null;
        }

        TestableDBHealthCheck(SQLException failure) {
            this.connection = null;
            this.failure = failure;
        }

        @Override
        protected void setupDBDriver() throws SQLException {
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        protected Connection getConnection() {
            return connection;
        }
    }
}
