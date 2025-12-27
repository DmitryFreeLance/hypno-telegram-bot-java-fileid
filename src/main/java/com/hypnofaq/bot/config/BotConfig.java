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

    // images for step 2 and step 5
    public final String checkupImagePath; // 2.jpg
    public final String annaImagePath;    // 5.jpg

    public final int videoPostId;

    public final String annaPostUrl;
    public final String maximPostUrl;

    // NEW: message ids in FAQ channel (derived from URL or ENV override)
    public final int annaPostId;
    public final int maximPostId;

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
            String checkupImagePath,
            String annaImagePath,
            int videoPostId,
            String annaPostUrl,
            String maximPostUrl,
            int annaPostId,
            int maximPostId,
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
        this.checkupImagePath = checkupImagePath;
        this.annaImagePath = annaImagePath;
        this.videoPostId = videoPostId;
        this.annaPostUrl = annaPostUrl;
        this.maximPostUrl = maximPostUrl;
        this.annaPostId = annaPostId;
        this.maximPostId = maximPostId;
        this.bookUsername = bookUsername;
        this.schedulerPollSeconds = schedulerPollSeconds;
        this.logLevel = logLevel;
    }

    public static BotConfig fromEnv() {
        String token = required("BOT_TOKEN");
        String username = required("BOT_USERNAME");

        String dbPath = env("DB_PATH").orElse("/data/bot.db");

        long channelId = parseLong(env("CHANNEL_ID").orElse("-1003060928185"));
        long faqChannelId = parseLong(env("FAQ_CHANNEL_ID").orElse(String.valueOf(channelId)));

        String practiceAudioPath = env("PRACTICE_AUDIO_PATH").orElse("/assets/Встреча с будущим Я.m4a");
        String checkupPdfPath = env("CHECKUP_PDF_PATH").orElse("/assets/ЧЕК-АП.pdf");

        String checkupImagePath = env("CHECKUP_IMAGE_PATH").orElse("/assets/2.jpg");
        String annaImagePath = env("ANNA_IMAGE_PATH").orElse("/assets/5.jpg");

        int videoPostId = parseInt(env("VIDEO_POST_ID").orElse("135"));

        String annaUrl = env("ANNA_POST_URL").orElse("https://t.me/hypno_FAQ/112");
        String maximUrl = env("MAXIM_POST_URL").orElse("https://t.me/hypno_FAQ/140");

        // NEW: allow explicit message ids, otherwise parse from URL
        int annaPostId = env("ANNA_POST_ID")
                .map(BotConfig::parseInt)
                .orElseGet(() -> parseTelegramPostId(annaUrl).orElse(112));

        int maximPostId = env("MAXIM_POST_ID")
                .map(BotConfig::parseInt)
                .orElseGet(() -> parseTelegramPostId(maximUrl).orElse(140));

        String bookUsername = env("BOOK_USERNAME").orElse("katherine_hypno");
        int pollSeconds = parseInt(env("SCHEDULER_POLL_SECONDS").orElse("10"));

        String logLevel = env("LOG_LEVEL").orElse("INFO").toUpperCase(Locale.ROOT);

        return new BotConfig(
                token, username, dbPath, channelId, faqChannelId,
                practiceAudioPath, checkupPdfPath,
                checkupImagePath, annaImagePath,
                videoPostId,
                annaUrl, maximUrl,
                annaPostId, maximPostId,
                bookUsername, pollSeconds, logLevel
        );
    }

    private static Optional<Integer> parseTelegramPostId(String url) {
        if (url == null) return Optional.empty();
        try {
            String u = url.trim();
            int slash = u.lastIndexOf('/');
            if (slash < 0 || slash == u.length() - 1) return Optional.empty();
            String tail = u.substring(slash + 1).trim();
            if (tail.isEmpty()) return Optional.empty();
            return Optional.of(Integer.parseInt(tail));
        } catch (Exception ignored) {
            return Optional.empty();
        }
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