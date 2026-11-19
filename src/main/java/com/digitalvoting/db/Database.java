package com.digitalvoting.db;

import com.digitalvoting.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Single place that opens JDBC connections to MySQL. Connection details come from config.properties. */
public final class Database {
    private Database() {}

    private static String url() {
        return "jdbc:mysql://" + AppConfig.get("db.host", "localhost") + ":" + AppConfig.get("db.port", "3306")
                + "/" + AppConfig.get("db.name", "digital_voting")
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata&characterEncoding=UTF-8";
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(url(), AppConfig.get("db.user", "root"), AppConfig.get("db.password", ""));
    }

    /** @return null if the database is reachable, otherwise a readable error message. */
    public static String ping() {
        try (Connection c = get()) {
            return c.isValid(3) ? null : "Connection invalid";
        } catch (SQLException e) {
            return e.getMessage();
        }
    }
}
