package com.hypnofaq.bot.db;

import com.hypnofaq.bot.model.Job;
import com.hypnofaq.bot.model.JobType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class JobDao {
    private final Database db;

    public JobDao(Database db) {
        this.db = db;
    }

    public void scheduleOnce(long tgId, JobType type, long runAtMillis, String payload) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT OR IGNORE INTO jobs (tg_id, type, run_at, payload, status, attempts, created_at, updated_at)
                     VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)
                     """)) {
            ps.setLong(1, tgId);
            ps.setString(2, type.name());
            ps.setLong(3, runAtMillis);
            ps.setString(4, payload);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("scheduleOnce failed", e);
        }
    }

    public List<Job> fetchDueJobs(long nowMillis, int limit) {
        List<Job> jobs = new ArrayList<>();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT id, tg_id, type, run_at, payload, attempts
                     FROM jobs
                     WHERE status = 'PENDING' AND run_at <= ?
                     ORDER BY run_at ASC
                     LIMIT ?
                     """)) {
            ps.setLong(1, nowMillis);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    long tgId = rs.getLong("tg_id");
                    JobType type = JobType.valueOf(rs.getString("type"));
                    long runAt = rs.getLong("run_at");
                    String payload = rs.getString("payload");
                    int attempts = rs.getInt("attempts");
                    jobs.add(new Job(id, tgId, type, runAt, payload, attempts));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("fetchDueJobs failed", e);
        }
        return jobs;
    }

    /**
     * Returns true if status changed to RUNNING (still was PENDING).
     */
    public boolean markRunning(long jobId) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE jobs SET status = 'RUNNING', updated_at = ?
                     WHERE id = ? AND status = 'PENDING'
                     """)) {
            ps.setLong(1, now);
            ps.setLong(2, jobId);
            int updated = ps.executeUpdate();
            return updated == 1;
        } catch (Exception e) {
            throw new RuntimeException("markRunning failed", e);
        }
    }

    public void markDone(long jobId) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE jobs SET status = 'DONE', updated_at = ?, last_error = NULL
                     WHERE id = ?
                     """)) {
            ps.setLong(1, now);
            ps.setLong(2, jobId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("markDone failed", e);
        }
    }

    public void rescheduleWithError(long jobId, int newAttempts, long newRunAt, String error) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE jobs
                     SET status = 'PENDING',
                         attempts = ?,
                         run_at = ?,
                         last_error = ?,
                         updated_at = ?
                     WHERE id = ?
                     """)) {
            ps.setInt(1, newAttempts);
            ps.setLong(2, newRunAt);
            ps.setString(3, error);
            ps.setLong(4, now);
            ps.setLong(5, jobId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("rescheduleWithError failed", e);
        }
    }

    public void markFailed(long jobId, int attempts, String error) {
        long now = Instant.now().toEpochMilli();
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE jobs
                     SET status = 'FAILED',
                         attempts = ?,
                         last_error = ?,
                         updated_at = ?
                     WHERE id = ?
                     """)) {
            ps.setInt(1, attempts);
            ps.setString(2, error);
            ps.setLong(3, now);
            ps.setLong(4, jobId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("markFailed failed", e);
        }
    }
}
