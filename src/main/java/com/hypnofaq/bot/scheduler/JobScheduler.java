package com.hypnofaq.bot.scheduler;

import com.hypnofaq.bot.db.JobDao;
import com.hypnofaq.bot.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JobScheduler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    public interface Handler {
        void handle(Job job) throws Exception;
    }

    private final JobDao jobDao;
    private final Handler handler;
    private final int pollSeconds;

    private final Thread thread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JobScheduler(JobDao jobDao, Handler handler, int pollSeconds) {
        this.jobDao = jobDao;
        this.handler = handler;
        this.pollSeconds = Math.max(1, pollSeconds);
        this.thread = new Thread(this, "job-scheduler");
        this.thread.setDaemon(true);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            thread.start();
            log.info("JobScheduler started (poll every {}s)", pollSeconds);
        }
    }

    public void stop() {
        running.set(false);
        thread.interrupt();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                long now = Instant.now().toEpochMilli();
                List<Job> due = jobDao.fetchDueJobs(now, 50);

                for (Job job : due) {
                    if (!running.get()) break;

                    // Try to "lock" job
                    boolean locked = jobDao.markRunning(job.id);
                    if (!locked) continue;

                    try {
                        handler.handle(job);
                        jobDao.markDone(job.id);
                    } catch (Exception e) {
                        int attempts = job.attempts + 1;
                        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                        log.warn("Job {} for tgId={} failed (attempt {}): {}", job.type, job.tgId, attempts, msg);

                        // 403 (bot blocked) — no retries
                        if (msg.contains("403") || msg.toLowerCase().contains("bot was blocked")) {
                            jobDao.markFailed(job.id, attempts, msg);
                            continue;
                        }

                        if (attempts >= 5) {
                            jobDao.markFailed(job.id, attempts, msg);
                        } else {
                            long retryAt = Instant.now().plusSeconds(60L * 5).toEpochMilli(); // 5 minutes
                            jobDao.rescheduleWithError(job.id, attempts, retryAt, msg);
                        }
                    }
                }

                Thread.sleep(pollSeconds * 1000L);
            } catch (InterruptedException ie) {
                // shutdown
            } catch (Exception e) {
                log.error("Scheduler loop error: {}", e.getMessage(), e);
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException ignored) {}
            }
        }
        log.info("JobScheduler stopped.");
    }
}
