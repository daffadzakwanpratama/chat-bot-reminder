package com.bot.reminder.service;

import com.bot.reminder.dto.GeminiTaskResponse;
import com.bot.reminder.model.Task;
import com.bot.reminder.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TelegramBotService extends TelegramLongPollingBot {

    // Menggunakan nama 'logger' agar tidak konflik dengan field private 'log' milik superclass DefaultAbsSender
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    private final String botToken;
    private final String botUsername;
    private final TaskRepository taskRepository;
    private final GeminiService geminiService;

    public TelegramBotService(
            @Value("${TELEGRAM_BOT_TOKEN}") String botToken,
            @Value("${TELEGRAM_BOT_USERNAME}") String botUsername,
            TaskRepository taskRepository,
            GeminiService geminiService) {
        super(botToken);
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.taskRepository = taskRepository;
        this.geminiService = geminiService;
        logger.info("Telegram Bot Service diinisialisasi untuk bot: {}", botUsername);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Hanya memproses pesan teks masuk
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();
            Long chatId = update.getMessage().getChatId();
            String userName = update.getMessage().getFrom().getFirstName();

            logger.info("Menerima pesan dari {}: '{}'", userName, messageText);

            if (messageText.startsWith("/start")) {
                sendWelcomeMessage(chatId, userName);
            } else if (messageText.startsWith("/daftar")) {
                handleDaftar(chatId);
            } else if (messageText.startsWith("/hapus")) {
                handleHapus(chatId, messageText);
            } else {
                // Semua pesan biasa dianggap sebagai input tugas berbasis AI (Natural Language)
                handleNaturalLanguageTask(chatId, messageText);
            }
        }
    }

    /**
     * Mengirim pesan selamat datang dan panduan penggunaan bot.
     */
    private void sendWelcomeMessage(Long chatId, String name) {
        String welcome = "👋 Halo <b>" + name + "</b>!\n\n"
                + "Saya adalah <b>Task Reminder Bot</b> berbasis AI. "
                + "Saya bisa mencatat tugas Anda hanya dengan bahasa santai sehari-hari!\n\n"
                + "📋 <b>Fitur yang bisa Anda gunakan:</b>\n"
                + "1️⃣ <b>Tambah Tugas (AI)</b>\n"
                + "Cukup ketik pesan biasa, contoh:\n"
                + "• <i>\"ingatkan saya membuat laporan KKN besok jam 7 malam\"</i>\n"
                + "• <i>\"olahraga pagi 2 menit lagi\"</i>\n"
                + "• <i>\"beli susu nanti jam 16.30\"</i>\n\n"
                + "2️⃣ <b>Lihat Tugas Aktif</b>\n"
                + "Ketik perintah: <code>/daftar</code>\n\n"
                + "3️⃣ <b>Hapus Tugas</b>\n"
                + "Ketik perintah: <code>/hapus [nomor]</code>\n"
                + "Contoh: <code>/hapus 1</code> untuk menghapus tugas nomor 1 di daftar.\n\n"
                + "Silakan coba ketik tugas pertama Anda! 🚀";

        sendMessage(chatId, welcome);
    }

    /**
     * Menampilkan daftar semua tugas aktif (belum diingatkan) milik user.
     */
    private void handleDaftar(Long chatId) {
        List<Task> tasks = taskRepository.findByChatIdAndNotifiedFalseOrderByReminderTimeAsc(chatId);

        if (tasks.isEmpty()) {
            sendMessage(chatId, "📭 <b>Daftar tugas Anda kosong!</b>\n\nKetik perintah seperti <i>\"ingatkan saya belajar Java besok malam jam 8\"</i> untuk menambah tugas baru.");
            return;
        }

        StringBuilder response = new StringBuilder("📝 <b>Daftar Tugas Aktif Anda:</b>\n\n");
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            response.append(i + 1).append(". <b>").append(task.getDescription()).append("</b>\n");
            if (task.getNotes() != null && !task.getNotes().trim().isEmpty()) {
                response.append("   ℹ️ <i>Catatan: ").append(task.getNotes()).append("</i>\n");
            }
            response.append("   ⏰ ").append(formatIndonesianDateTime(task.getReminderTime())).append("\n\n");
        }

        sendMessage(chatId, response.toString());
    }

    /**
     * Menghapus tugas aktif berdasarkan nomor indeks dari daftar.
     */
    private void handleHapus(Long chatId, String messageText) {
        String[] parts = messageText.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "⚠️ Format salah. Gunakan: <code>/hapus [nomor]</code>\nContoh: <code>/hapus 1</code>");
            return;
        }

        try {
            int index = Integer.parseInt(parts[1]) - 1;
            List<Task> tasks = taskRepository.findByChatIdAndNotifiedFalseOrderByReminderTimeAsc(chatId);

            if (index < 0 || index >= tasks.size()) {
                sendMessage(chatId, "❌ <b>Nomor tugas tidak ditemukan.</b> Silakan cek daftar tugas Anda dengan perintah <code>/daftar</code>.");
                return;
            }

            Task taskToDelete = tasks.get(index);
            taskRepository.delete(taskToDelete);

            sendMessage(chatId, "✅ <b>Tugas berhasil dihapus!</b>\n🗑️ <i>\"" + taskToDelete.getDescription() + "\"</i>");
        } catch (NumberFormatException e) {
            sendMessage(chatId, "⚠️ Nomor tugas harus berupa angka. Contoh: <code>/hapus 1</code>");
        }
    }

    /**
     * Memproses teks bebas menggunakan Gemini AI untuk mengekstrak tugas dan waktu.
     */
    private void handleNaturalLanguageTask(Long chatId, String messageText) {
        // Membersihkan awalan "/tambah" jika user menggunakannya secara manual
        String cleanedText = messageText;
        if (messageText.toLowerCase().startsWith("/tambah")) {
            cleanedText = messageText.substring(7).trim();
        }

        sendMessage(chatId, "🤖 <i>Sedang menganalisis tugas Anda...</i>");

        GeminiTaskResponse response = geminiService.parseTask(cleanedText);

        if (response == null || "FAILED".equals(response.getStatus()) || response.getTask() == null || response.getDatetime() == null) {
            String fallbackMessage = "❌ <b>Bot tidak memahami tugas atau waktu pengingat.</b>\n\n"
                    + "Pastikan Anda menyertakan deskripsi tugas dan kapan harus diingatkan secara jelas.\n"
                    + "💡 <b>Contoh yang benar:</b>\n"
                    + "• <i>\"ingatkan saya belajar java besok jam 7 malam\"</i>\n"
                    + "• <i>\"buat kopi 5 menit lagi\"</i>";
            sendMessage(chatId, fallbackMessage);
            return;
        }

        try {
            // Parsing String datetime dari Gemini ("yyyy-MM-dd HH:mm") menjadi LocalDateTime
            LocalDateTime reminderTime = LocalDateTime.parse(response.getDatetime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            if (reminderTime.isBefore(LocalDateTime.now())) {
                sendMessage(chatId, "⚠️ <b>Waktu pengingat sudah lewat!</b> Pastikan Anda menentukan waktu di masa depan.");
                return;
            }

            // Menyimpan tugas ke database SQLite (Menggunakan Setter standar, tanpa Lombok Builder)
            Task task = new Task();
            task.setChatId(chatId);
            task.setDescription(response.getTask());
            task.setNotes(response.getNotes());
            task.setReminderTime(reminderTime);
            task.setNotified(false);

            taskRepository.save(task);

            StringBuilder successMessage = new StringBuilder("✅ <b>Tugas berhasil ditambahkan!</b>\n\n"
                    + "📝 <b>Tugas:</b> " + response.getTask() + "\n");
            if (response.getNotes() != null && !response.getNotes().trim().isEmpty()) {
                successMessage.append("ℹ️ <b>Catatan:</b> ").append(response.getNotes()).append("\n");
            }
            successMessage.append("⏰ <b>Waktu:</b> ").append(formatIndonesianDateTime(reminderTime));

            sendMessage(chatId, successMessage.toString());
        } catch (Exception e) {
            logger.error("Gagal menyimpan tugas", e);
            sendMessage(chatId, "❌ Terjadi kesalahan sistem saat menyimpan tugas Anda.");
        }
    }

    /**
     * Mengirimkan pesan keluar ke chat ID tertentu dengan format HTML.
     */
    public void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML")
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Gagal mengirim pesan ke chatId {}", chatId, e);
        }
    }

    /**
     * Memformat objek LocalDateTime menjadi format bahasa Indonesia yang indah.
     */
    private String formatIndonesianDateTime(LocalDateTime dateTime) {
        String[] months = {
                "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        };
        return dateTime.getDayOfMonth() + " " + months[dateTime.getMonthValue() - 1] + " " + dateTime.getYear() + ", "
                + String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute()) + " WIB";
    }
}
