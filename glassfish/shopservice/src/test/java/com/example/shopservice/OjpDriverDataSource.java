package com.example.shopservice;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Minimal {@link DataSource} adapter for the OJP JDBC Driver, used in tests only.
 *
 * <p>GlassFish's {@code @DataSourceDefinition} requires a {@link DataSource} implementation
 * class name, but OJP only ships a {@link java.sql.Driver}.  This adapter delegates every
 * {@link #getConnection()} call to {@link DriverManager}, which loads the OJP driver
 * automatically via the standard JDBC SPI mechanism.
 *
 * <p>GlassFish populates {@code url}, {@code user} and {@code password} via JavaBean
 * setters from the {@code @DataSourceDefinition} properties.
 */
public class OjpDriverDataSource implements DataSource {

    private String url;
    private String user;
    private String password;
    private PrintWriter logWriter;

    public void setUrl(String url)           { this.url = url; }
    public void setUser(String user)         { this.user = user; }
    public void setPassword(String password) { this.password = password; }

    @Override
    public Connection getConnection() throws SQLException {
        if (url == null || url.isBlank()) {
            throw new SQLException("OjpDriverDataSource: 'url' property is not set");
        }
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        if (url == null || url.isBlank()) {
            throw new SQLException("OjpDriverDataSource: 'url' property is not set");
        }
        return DriverManager.getConnection(url, username, password);
    }

    @Override public PrintWriter getLogWriter()             { return logWriter; }
    @Override public void        setLogWriter(PrintWriter w){ this.logWriter = w; }
    @Override public int         getLoginTimeout()          { return 0; }
    @Override public void        setLoginTimeout(int s)     {}

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger not supported");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) return iface.cast(this);
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
