package com.hypnofaq.bot;

import com.hypnofaq.bot.config.BotConfig;
import com.hypnofaq.bot.db.Database;
import com.hypnofaq.bot.db.FileIdDao;
import com.hypnofaq.bot.db.JobDao;
import com.hypnofaq.bot.db.UserDao;
import com.hypnofaq.bot.scheduler.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        BotConfig config = BotConfig.fromEnv();

        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", config.logLevel.toLowerCase());

        Database db = new Database(config.dbPath);
        db.initSchema();

        UserDao userDao = new UserDao(db);
        JobDao jobDao = new JobDao(db);
        FileIdDao fileIdDao = new FileIdDao(db);

        HypnoBot bot = new HypnoBot(config, userDao, jobDao, fileIdDao);

        JobScheduler scheduler = new JobScheduler(jobDao, bot::handleJob, config.schedulerPollSeconds);
        scheduler.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown...");
            scheduler.stop();
        }));

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);

        log.info("Bot started as @{}", config.botUsername);
    }
}
