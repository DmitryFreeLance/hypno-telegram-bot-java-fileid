package com.hypnofaq.bot.config;

import java.util.Locale;
import java.util.Optional;

public final class BotConfig {
    public final String botToken;
    public final String botUsername;

    public final String dbPath;

    public final long channelId;
    public final long faqChannelId;

    public final String practiceAudioPath;
    public final String checkupPdfPath;

    public final int videoPostId;

    public final String annaPostUrl;
    public final String maximPostUrl;

    public final String bookUsername;
    public final int schedulerPollSeconds;

    public final String logLevel;

    private BotConfig(
            String botToken,
            String botUsername,
            String dbPath,
            long channelId,
            long faqChannelId,
            String practiceAudioPath,
            String checkupPdfPath,
            int videoPostId,
            String annaPostUrl,
            String maximPostUrl,
            String bookUsername,
            int schedulerPollSeconds,
            String logLevel
    ) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.dbPath = dbPath;
        this.channelId = channelId;
        this.faqChannelId = faqChannelId;
        this.practiceAudioPath = practiceAudioPath;
        this.checkupPdfPath = checkupPdfPath;
        this.videoPostId = videoPostId;
        this.annaPostUrl = annaPostUrl;
        this.maximPostUrl = maximPostUrl;
        this.bookUsername = bookUsername;
        this.schedulerPollSeconds = schedulerPollSeconds;
        this.logLevel = logLevel;
    }

    public static BotConfig fromEnv() {
        String token = required("BOT_TOKEN");
        String username = required("BOT_USERNAME");

        String dbPath = env("DB_PATH").orElse("/data/bot.db");

        long channelId = parseLong(env("CHANNEL_ID").orElse("-1003603335269"));
        long faqChannelId = parseLong(env("FAQ_CHANNEL_ID").orElse(String.valueOf(channelId)));

        String practiceAudioPath = env("PRACTICE_AUDIO_PATH").orElse("/assets/Встреча с будущим Я.m4a");
        String checkupPdfPath = env("CHECKUP_PDF_PATH").orElse("/assets/ЧЕК-АП.pdf");

        int videoPostId = parseInt(env("VIDEO_POST_ID").orElse("135"));

        String annaUrl = env("ANNA_POST_URL").orElse("https://t.me/hypno_FAQ/112");
        String maximUrl = env("MAXIM_POST_URL").orElse("https://t.me/hypno_FAQ/140");

        String bookUsername = env("BOOK_USERNAME").orElse("katherine_hypno");
        int pollSeconds = parseInt(env("SCHEDULER_POLL_SECONDS").orElse("10"));

        String logLevel = env("LOG_LEVEL").orElse("INFO").toUpperCase(Locale.ROOT);

        return new BotConfig(
                token, username, dbPath, channelId, faqChannelId,
                practiceAudioPath, checkupPdfPath, videoPostId,
                annaUrl, maximUrl, bookUsername, pollSeconds, logLevel
        );
    }

    private static Optional<String> env(String name) {
        return Optional.ofNullable(System.getenv(name)).map(String::trim).filter(s -> !s.isEmpty());
    }

    private static String required(String name) {
        return env(name).orElseThrow(() -> new IllegalStateException("Missing required ENV variable: " + name));
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid long ENV value: " + s, e);
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid int ENV value: " + s, e);
        }
    }
}