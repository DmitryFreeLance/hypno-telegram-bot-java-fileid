package com.hypnofaq.bot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

public final class FileIdDao {
    private final Database db;

    public FileIdDao(Database db) {
        this.db = db;
    }

    public Optional<String> getFileId(String key) {
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT file_id FROM file_cache WHERE key = ?
                     """)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String id = rs.getString("file_id");
                return Optional.ofNullable(id).map(String::trim).filter(s -> !s.isEmpty());
            }
        } catch (Exception e) {
            throw new RuntimeException("getFileId failed", e);
        }
    }

    public void upsertFileId(String key, String fileId) {
        if (key == null || key.isBlank()) return;
        if (fileId == null || fileId.isBlank()) return;

        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO file_cache (key, file_id, updated_at)
                     VALUES (?, ?, ?)
                     ON CONFLICT(key) DO UPDATE SET
                         file_id = excluded.file_id,
                         updated_at = excluded.updated_at
                     """)) {
            ps.setString(1, key);
            ps.setString(2, fileId);
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("upsertFileId failed", e);
        }
    }
}
