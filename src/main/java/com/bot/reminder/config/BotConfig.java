package com.bot.reminder.config;

import com.bot.reminder.service.TelegramBotService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotConfig {

    /**
     * Mendaftarkan bot Telegram secara manual menggunakan DefaultBotSession.
     * Ini menjamin bot terhubung ke API Telegram dan aktif menerima pesan (long polling).
     */
    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotService telegramBotService) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            botsApi.registerBot(telegramBotService);
            System.out.println("🤖 Bot Telegram BERHASIL didaftarkan secara manual ke Telegram API!");
        } catch (TelegramApiException e) {
            System.err.println("❌ Gagal mendaftarkan Bot Telegram: " + e.getMessage());
            throw e;
        }
        return botsApi;
    }
}
