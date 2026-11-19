package com.digitalvoting.config;

import java.io.*;
import java.util.Properties;

/** Loads config.properties (working dir, then classpath). Environment variables override file values. */
public final class AppConfig {
    private static final Properties P = new Properties();

    static {
        File f = new File("config.properties");
        try {
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) { P.load(in); }
            } else {
                try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
                    if (in != null) P.load(in);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read config.properties", e);
        }
    }

    private AppConfig() {}

    public static String get(String key, String def) {
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) return env;
        String v = P.getProperty(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
    public static String get(String key) { return get(key, ""); }
    public static int getInt(String key, int def) {
        try { return Integer.parseInt(get(key, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }
    public static double getDouble(String key, double def) {
        try { return Double.parseDouble(get(key, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }
    public static boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)));
    }
}
