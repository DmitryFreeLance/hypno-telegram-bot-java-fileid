package com.hypnofaq.bot;

import com.hypnofaq.bot.config.BotConfig;
import com.hypnofaq.bot.db.FileIdDao;
import com.hypnofaq.bot.db.JobDao;
import com.hypnofaq.bot.db.UserDao;
import com.hypnofaq.bot.model.Job;
import com.hypnofaq.bot.model.JobType;
import com.hypnofaq.bot.model.User;
import com.hypnofaq.bot.model.UserStage;
import com.hypnofaq.bot.ui.Keyboards;
import com.hypnofaq.bot.ui.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public final class HypnoBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(HypnoBot.class);

    // Callback data
    private static final String CB_START = "START";
    private static final String CB_CHECK_SUB = "CHECK_SUB";
    private static final String CB_GET_PRACTICE = "GET_PRACTICE";
    private static final String CB_DOWNLOAD_PDF = "DOWNLOAD_PDF";
    private static final String CB_WATCH_VIDEO = "WATCH_VIDEO";
    private static final String CB_CHOOSE_TIME = "CHOOSE_TIME";

    // Keys for file_id cache
    private static final String FILEKEY_PRACTICE_AUDIO = "practice_audio";
    private static final String FILEKEY_CHECKUP_PDF = "checkup_pdf";

    private final BotConfig config;
    private final UserDao userDao;
    private final JobDao jobDao;
    private final FileIdDao fileIdDao;

    public HypnoBot(BotConfig config, UserDao userDao, JobDao jobDao, FileIdDao fileIdDao) {
        this.config = config;
        this.userDao = userDao;
        this.jobDao = jobDao;
        this.fileIdDao = fileIdDao;
    }

    @Override
    public String getBotUsername() {
        return config.botUsername;
    }

    @Override
    public String getBotToken() {
        return config.botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                long chatId = update.getMessage().getChatId();
                long userId = update.getMessage().getFrom().getId();
                String text = update.getMessage().getText().trim();

                userDao.ensureUser(userId);

                if (isStartCommand(text)) {
                    Optional<String> payloadOpt = parseStartPayload(text);

                    if (payloadOpt.isEmpty()) {
                        // обычный /start
                        userDao.clearStartParam(userId);
                        sendWelcome(chatId);
                        return;
                    }

                    // deep-link /start 2
                    String payload = payloadOpt.get();
                    userDao.setStartParam(userId, payload);

                    // сразу идем в проверку подписки
                    handleStartOrCheckSub(chatId, userId);
                    return;
                }

                if (text.equalsIgnoreCase("старт")) {
                    handleStartOrCheckSub(chatId, userId);
                    return;
                }

                return;
            }

            if (update.hasCallbackQuery()) {
                CallbackQuery cq = update.getCallbackQuery();
                String data = cq.getData();
                long chatId = cq.getMessage().getChatId();
                long userId = cq.getFrom().getId();

                userDao.ensureUser(userId);

                switch (data) {
                    case CB_START -> {
                        ack(cq.getId());
                        // Кнопка "Старт" — обычный сценарий
                        userDao.clearStartParam(userId);
                        handleStartOrCheckSub(chatId, userId);
                    }
                    case CB_CHECK_SUB -> {
                        ack(cq.getId());
                        handleStartOrCheckSub(chatId, userId);
                    }
                    case CB_GET_PRACTICE -> {
                        ack(cq.getId());
                        handleGetPractice(chatId, userId);
                    }
                    case CB_DOWNLOAD_PDF -> {
                        ack(cq.getId());
                        handleDownloadPdf(chatId, userId);
                    }
                    case CB_WATCH_VIDEO -> {
                        ack(cq.getId());
                        handleWatchVideo(chatId, userId);
                    }
                    case CB_CHOOSE_TIME -> {
                        ack(cq.getId());
                        handleChooseTime(chatId, userId);
                    }
                    default -> ack(cq.getId());
                }
            }
        } catch (Exception e) {
            log.error("onUpdateReceived error: {}", e.getMessage(), e);
        }
    }

    /* ---------------------------
       Public job handler for scheduler
       --------------------------- */

    public void handleJob(Job job) throws Exception {
        userDao.ensureUser(job.tgId);
        Optional<User> uOpt = userDao.getUser(job.tgId);
        if (uOpt.isEmpty()) return;
        User u = uOpt.get();

        switch (job.type) {
            case SEND_PRACTICE_INTRO -> {
                // Deep-link start=2: через 3 часа присылаем шаг 1
                // Если практику уже отправили (аудио), не дублируем
                if (u.practiceSentAt != null) return;

                sendText(job.tgId, Texts.PRACTICE_INTRO, null,
                        Keyboards.singleCallbackButton("Получить практику", CB_GET_PRACTICE),
                        true);

                // stage можно не трогать строго, но логично пометить, что юзер готов
                if (u.stage == UserStage.NEW || u.stage == UserStage.WAITING_SUBSCRIBE) {
                    userDao.setStage(job.tgId, UserStage.READY);
                }
            }

            case SEND_CHECKUP_PROMPT -> {
                if (u.practiceSentAt == null) return;
                sendHtml(job.tgId, Texts.CHECKUP_PROMPT_HTML,
                        Keyboards.singleCallbackButton("👉 Скачать Чек-ап (PDF)", CB_DOWNLOAD_PDF),
                        true);
                userDao.setStage(job.tgId, UserStage.CHECKUP_PROMPT_SENT);
            }

            case SEND_VIDEO_PROMPT -> {
                if (u.checkupSentAt == null) return;
                sendHtml(job.tgId, Texts.VIDEO_PROMPT_HTML,
                        Keyboards.singleCallbackButton("📺 Смотреть объяснение", CB_WATCH_VIDEO),
                        true);
                userDao.setStage(job.tgId, UserStage.VIDEO_PROMPT_SENT);

                schedule(job.tgId, JobType.SEND_CALL_INVITE, Duration.ofHours(24), null);
            }

            case SEND_CALL_INVITE -> {
                String url = "https://t.me/" + config.bookUsername;

                sendHtml(job.tgId, Texts.CALL_INVITE_HTML,
                        Keyboards.singleUrlButton("👉 Забронировать 20 минут", url),
                        true);
                userDao.setStage(job.tgId, UserStage.CALL_INVITE_SENT);

                schedule(job.tgId, JobType.SEND_ANNA_STORY, Duration.ofHours(4), null);
            }

            case SEND_ANNA_STORY -> {
                sendText(job.tgId, Texts.ANNA_STORY + "\n" + config.annaPostUrl, null, null, true);
                userDao.setStage(job.tgId, UserStage.ANNA_STORY_SENT);

                schedule(job.tgId, JobType.SEND_MAXIM_STORY, Duration.ofHours(24), null);
            }

            case SEND_MAXIM_STORY -> {
                sendText(job.tgId, Texts.MAXIM_STORY + "\n" + config.maximPostUrl, null, null, true);
                userDao.setStage(job.tgId, UserStage.MAXIM_STORY_SENT);

                schedule(job.tgId, JobType.SEND_FINAL_PUSH, Duration.ofSeconds(5), null);
            }

            case SEND_FINAL_PUSH -> {
                sendHtml(job.tgId, Texts.FINAL_PUSH_HTML,
                        Keyboards.singleCallbackButton("Выбрать время", CB_CHOOSE_TIME),
                        true);
                userDao.setStage(job.tgId, UserStage.FINAL_PUSH_SENT);

                schedule(job.tgId, JobType.SEND_REMINDER_BOOK_TIME, Duration.ofHours(3), null);
            }

            case SEND_REMINDER_BOOK_TIME -> {
                if (u.chooseTimeClicked) return;

                sendHtml(job.tgId, Texts.REMINDER_HTML,
                        Keyboards.singleCallbackButton("Выбрать время", CB_CHOOSE_TIME),
                        true);
            }
        }
    }

    /* ---------------------------
       Core flow
       --------------------------- */

    private void sendWelcome(long chatId) throws TelegramApiException {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(Texts.WELCOME);
        msg.setReplyMarkup(Keyboards.singleCallbackButton("Старт", CB_START));
        msg.setDisableWebPagePreview(true);
        execute(msg);
    }

    private void handleStartOrCheckSub(long chatId, long userId) throws TelegramApiException {
        boolean subscribed;
        try {
            subscribed = isSubscribed(userId);
        } catch (TelegramApiException e) {
            log.warn("Subscription check failed: {}", e.getMessage());
            String txt = """
Сейчас я не могу проверить подписку 😔

Похоже, у меня нет доступа к списку участников канала.
Попросите администратора канала добавить этого бота в канал как администратора, и проверка заработает ✅
""";
            sendText(chatId, txt, null, null, true);
            return;
        }

        userDao.setSubscribed(userId, subscribed);

        if (!subscribed) {
            userDao.setStage(userId, UserStage.WAITING_SUBSCRIBE);
            sendText(chatId, Texts.NEED_SUBSCRIBE, null,
                    Keyboards.singleCallbackButton("✅ Я подписалась", CB_CHECK_SUB),
                    true);
            return;
        }

        Optional<User> uOpt = userDao.getUser(userId);
        String startParam = uOpt.map(u -> u.startParam).orElse(null);

        if ("2".equals(startParam)) {
            // start=2: сразу шаг2, а шаг1 через 3 часа
            userDao.clearStartParam(userId);
            startFromStep2WithDelayForStep1(chatId, userId);
            return;
        }

        // default
        userDao.setStage(userId, UserStage.READY);
        sendText(chatId, Texts.PRACTICE_INTRO, null,
                Keyboards.singleCallbackButton("Получить практику", CB_GET_PRACTICE),
                true);
    }

    /**
     * Deep-link "2":
     *  - send step2 now (check-up prompt + PDF button)
     *  - schedule step1 (practice intro) after 3 hours
     */
    private void startFromStep2WithDelayForStep1(long chatId, long userId) throws TelegramApiException {
        // step 2 now
        sendHtml(chatId, Texts.CHECKUP_PROMPT_HTML,
                Keyboards.singleCallbackButton("👉 Скачать Чек-ап (PDF)", CB_DOWNLOAD_PDF),
                true);
        userDao.setStage(userId, UserStage.CHECKUP_PROMPT_SENT);

        // step 1 after 3 hours
        schedule(userId, JobType.SEND_PRACTICE_INTRO, Duration.ofHours(3), null);
    }

    private void handleGetPractice(long chatId, long userId) throws TelegramApiException {
        SendAudio audio = new SendAudio();
        audio.setChatId(String.valueOf(chatId));
        audio.setCaption("🎧 Встреча с будущим Я");

        Optional<String> cached = fileIdDao.getFileId(FILEKEY_PRACTICE_AUDIO);
        if (cached.isPresent()) {
            audio.setAudio(new InputFile(cached.get()));
        } else {
            File f = new File(config.practiceAudioPath);
            if (!f.exists() || !f.isFile()) {
                String txt = "Не могу найти файл практики на сервере 😔\n" +
                        "Администратору нужно загрузить файл или один раз отправить его боту, чтобы сохранился file_id.";
                sendText(chatId, txt, null, null, true);
                return;
            }
            audio.setAudio(new InputFile(f));
        }

        var sent = execute(audio);
        if (sent != null && sent.getAudio() != null && sent.getAudio().getFileId() != null) {
            fileIdDao.upsertFileId(FILEKEY_PRACTICE_AUDIO, sent.getAudio().getFileId());
        }

        sendHtml(chatId, Texts.PRACTICE_INSTRUCTION_HTML, null, false);

        userDao.markPracticeSent(userId);

        // если чек-ап уже был отправлен/скачан, не шлём заново чек-ап через 24 часа
        Optional<User> uOpt = userDao.getUser(userId);
        boolean alreadyHasCheckup = uOpt.map(u -> u.checkupSentAt != null).orElse(false);
        if (!alreadyHasCheckup) {
            schedule(userId, JobType.SEND_CHECKUP_PROMPT, Duration.ofHours(24), null);
        }
    }

    private void handleDownloadPdf(long chatId, long userId) throws TelegramApiException {
        SendDocument doc = new SendDocument();
        doc.setChatId(String.valueOf(chatId));

        Optional<String> cached = fileIdDao.getFileId(FILEKEY_CHECKUP_PDF);
        if (cached.isPresent()) {
            doc.setDocument(new InputFile(cached.get()));
        } else {
            File f = new File(config.checkupPdfPath);
            if (!f.exists() || !f.isFile()) {
                String txt = "Не могу найти PDF «ЧЕК-АП» на сервере 😔\n" +
                        "Администратору нужно загрузить файл или один раз отправить его боту, чтобы сохранился file_id.";
                sendText(chatId, txt, null, null, true);
                return;
            }
            doc.setDocument(new InputFile(f));
        }

        var sent = execute(doc);
        if (sent != null && sent.getDocument() != null && sent.getDocument().getFileId() != null) {
            fileIdDao.upsertFileId(FILEKEY_CHECKUP_PDF, sent.getDocument().getFileId());
        }

        userDao.markCheckupSent(userId);
        schedule(userId, JobType.SEND_VIDEO_PROMPT, Duration.ofHours(4), null); // шаг 3
    }

    private void handleWatchVideo(long chatId, long userId) throws TelegramApiException {
        try {
            ForwardMessage fm = new ForwardMessage();
            fm.setChatId(String.valueOf(chatId));
            fm.setFromChatId(String.valueOf(config.faqChannelId));
            fm.setMessageId(config.videoPostId);
            execute(fm);
        } catch (TelegramApiException e) {
            log.warn("Forward failed, sending link instead: {}", e.getMessage());
            String link = "https://t.me/hypno_FAQ/" + config.videoPostId;
            sendText(chatId, link, null, null, true);
        }
    }

    private void handleChooseTime(long chatId, long userId) throws TelegramApiException {
        userDao.markChooseTimeClicked(userId);

        String url = "https://t.me/" + config.bookUsername;

        InlineKeyboardButton open = new InlineKeyboardButton();
        open.setText("✉️ Написать @" + config.bookUsername);
        open.setUrl(url);

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(java.util.List.of(java.util.List.of(open)));

        String txt = """
Отлично ✨

Нажми кнопку ниже — и откроется личный чат:
""";

        sendText(chatId, txt, null, kb, true);
    }

    /* ---------------------------
       Helpers
       --------------------------- */

    private void schedule(long tgId, JobType type, Duration delay, String payload) {
        long runAt = Instant.now().plus(delay).toEpochMilli();
        jobDao.scheduleOnce(tgId, type, runAt, payload);
    }

    private void ack(String callbackQueryId) {
        try {
            AnswerCallbackQuery ans = new AnswerCallbackQuery();
            ans.setCallbackQueryId(callbackQueryId);
            execute(ans);
        } catch (Exception ignored) {}
    }

    private boolean isSubscribed(long userId) throws TelegramApiException {
        GetChatMember gcm = new GetChatMember();
        gcm.setChatId(String.valueOf(config.channelId));
        gcm.setUserId(userId);

        ChatMember m = execute(gcm);
        String status = m.getStatus();
        if (status == null) return false;

        status = status.toLowerCase(Locale.ROOT);
        return !(status.equals("left") || status.equals("kicked"));
    }

    private void sendHtml(long chatId, String html, InlineKeyboardMarkup kb, boolean disablePreview) throws TelegramApiException {
        sendText(chatId, html, "HTML", kb, disablePreview);
    }

    private void sendText(long chatId, String text, String parseMode, InlineKeyboardMarkup kb, boolean disablePreview) throws TelegramApiException {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(text);
        if (parseMode != null) msg.setParseMode(parseMode);
        if (kb != null) msg.setReplyMarkup(kb);
        msg.setDisableWebPagePreview(disablePreview);
        execute(msg);
    }

    private static boolean isStartCommand(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (!t.startsWith("/start")) return false;
        return t.equals("/start") || t.startsWith("/start@") || t.startsWith("/start ");
    }

    private static Optional<String> parseStartPayload(String text) {
        if (text == null) return Optional.empty();
        String trimmed = text.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) return Optional.empty();
        String payload = parts[1].trim();
        if (payload.isEmpty()) return Optional.empty();
        return Optional.of(payload);
    }
}