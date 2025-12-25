package com.hypnofaq.bot.db;

import com.hypnofaq.bot.model.User;
import com.hypnofaq.bot.model.UserStage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

public final class UserDao {
    private final Database db;

    public UserDao(Database db) {
        this.db = db;
    }

    public void ensureUser(long tgId) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO users (tg_id, created_at, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(tg_id) DO UPDATE SET updated_at = excluded.updated_at
                    """)) {
                ps.setLong(1, tgId);
                ps.setLong(2, now);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("ensureUser failed", e);
        }
    }

    public Optional<User> getUser(long tgId) {
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT tg_id, stage, subscribed, practice_sent_at, checkup_sent_at, choose_time_clicked
                     FROM users WHERE tg_id = ?
                     """)) {
            ps.setLong(1, tgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                String stageStr = rs.getString("stage");
                UserStage stage;
                try {
                    stage = UserStage.valueOf(stageStr);
                } catch (Exception ignored) {
                    stage = UserStage.NEW;
                }

                boolean subscribed = rs.getInt("subscribed") == 1;
                Long practiceSentAt = (Long) rs.getObject("practice_sent_at");
                Long checkupSentAt = (Long) rs.getObject("checkup_sent_at");
                boolean chooseTimeClicked = rs.getInt("choose_time_clicked") == 1;

                return Optional.of(new User(
                        rs.getLong("tg_id"),
                        stage,
                        subscribed,
                        practiceSentAt,
                        checkupSentAt,
                        chooseTimeClicked
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("getUser failed", e);
        }
    }

    public void setStage(long tgId, UserStage stage) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE users SET stage = ?, updated_at = ? WHERE tg_id = ?
                     """)) {
            ps.setString(1, stage.name());
            ps.setLong(2, now);
            ps.setLong(3, tgId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("setStage failed", e);
        }
    }

    public void setSubscribed(long tgId, boolean subscribed) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE users SET subscribed = ?, updated_at = ? WHERE tg_id = ?
                     """)) {
            ps.setInt(1, subscribed ? 1 : 0);
            ps.setLong(2, now);
            ps.setLong(3, tgId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("setSubscribed failed", e);
        }
    }

    public void markPracticeSent(long tgId) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE users
                     SET practice_sent_at = ?, stage = ?, updated_at = ?
                     WHERE tg_id = ?
                     """)) {
            ps.setLong(1, now);
            ps.setString(2, UserStage.PRACTICE_SENT.name());
            ps.setLong(3, now);
            ps.setLong(4, tgId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("markPracticeSent failed", e);
        }
    }

    public void markCheckupSent(long tgId) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE users
                     SET checkup_sent_at = ?, stage = ?, updated_at = ?
                     WHERE tg_id = ?
                     """)) {
            ps.setLong(1, now);
            ps.setString(2, UserStage.CHECKUP_SENT.name());
            ps.setLong(3, now);
            ps.setLong(4, tgId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("markCheckupSent failed", e);
        }
    }

    public void markChooseTimeClicked(long tgId) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE users
                     SET choose_time_clicked = 1, updated_at = ?
                     WHERE tg_id = ?
                     """)) {
            ps.setLong(1, now);
            ps.setLong(2, tgId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("markChooseTimeClicked failed", e);
        }
    }
}
