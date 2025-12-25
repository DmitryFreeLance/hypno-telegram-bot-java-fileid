package com.hypnofaq.bot.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

public final class Keyboards {
    private Keyboards() {}

    public static InlineKeyboardMarkup singleCallbackButton(String text, String callbackData) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(callbackData);
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(b)));
        return m;
    }

    public static InlineKeyboardMarkup singleUrlButton(String text, String url) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setUrl(url);
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(b)));
        return m;
    }

    public static InlineKeyboardMarkup twoButtonsVertical(InlineKeyboardButton b1, InlineKeyboardButton b2) {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(b1),
                List.of(b2)
        ));
        return m;
    }

    public static InlineKeyboardButton callbackButton(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(data);
        return b;
    }

    public static InlineKeyboardButton urlButton(String text, String url) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setUrl(url);
        return b;
    }
}
