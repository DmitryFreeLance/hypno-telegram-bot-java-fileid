package com.hypnofaq.bot.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class Database {
    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final String jdbcUrl;

    public Database(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    public Connection openConnection() throws Exception {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("PRAGMA journal_mode = WAL;");
            st.execute("PRAGMA synchronous = NORMAL;");
        }
        return conn;
    }

    public void initSchema() {
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {

            st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        tg_id INTEGER PRIMARY KEY,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        stage TEXT NOT NULL DEFAULT 'NEW',
                        subscribed INTEGER NOT NULL DEFAULT 0,
                        practice_sent_at INTEGER,
                        checkup_sent_at INTEGER,
                        choose_time_clicked INTEGER NOT NULL DEFAULT 0,
                        start_param TEXT
                    );
                    """);

            // Migration for older DBs (if users table existed without start_param)
            try {
                st.execute("ALTER TABLE users ADD COLUMN start_param TEXT;");
                log.info("Migration applied: users.start_param added.");
            } catch (Exception ignored) {
                // column already exists -> ignore
            }

            st.execute("""
                    CREATE TABLE IF NOT EXISTS jobs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tg_id INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        run_at INTEGER NOT NULL,
                        payload TEXT,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        attempts INTEGER NOT NULL DEFAULT 0,
                        last_error TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        UNIQUE(tg_id, type),
                        FOREIGN KEY (tg_id) REFERENCES users(tg_id) ON DELETE CASCADE
                    );
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS file_cache (
                        key TEXT PRIMARY KEY,
                        file_id TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    );
                    """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_jobs_run_at ON jobs(status, run_at);");

            log.info("SQLite schema initialized.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to init SQLite schema", e);
        }
    }
}