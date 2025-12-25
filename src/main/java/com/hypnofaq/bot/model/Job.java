package com.hypnofaq.bot.model;

public final class Job {
    public final long id;
    public final long tgId;
    public final JobType type;
    public final long runAt;
    public final String payload;
    public final int attempts;

    public Job(long id, long tgId, JobType type, long runAt, String payload, int attempts) {
        this.id = id;
        this.tgId = tgId;
        this.type = type;
        this.runAt = runAt;
        this.payload = payload;
        this.attempts = attempts;
    }
}
