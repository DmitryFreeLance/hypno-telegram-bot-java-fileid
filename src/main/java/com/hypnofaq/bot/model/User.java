package com.hypnofaq.bot.model;

public final class User {
    public final long tgId;
    public final UserStage stage;
    public final boolean subscribed;
    public final Long practiceSentAt;
    public final Long checkupSentAt;
    public final boolean chooseTimeClicked;

    /** Deep-link payload saved between /start and subscription flow (e.g. "2") */
    public final String startParam;

    public User(long tgId,
                UserStage stage,
                boolean subscribed,
                Long practiceSentAt,
                Long checkupSentAt,
                boolean chooseTimeClicked,
                String startParam) {
        this.tgId = tgId;
        this.stage = stage;
        this.subscribed = subscribed;
        this.practiceSentAt = practiceSentAt;
        this.checkupSentAt = checkupSentAt;
        this.chooseTimeClicked = chooseTimeClicked;
        this.startParam = startParam;
    }
}