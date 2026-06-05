package com.bot.reminder.service;

import com.bot.reminder.dto.GeminiTaskResponse;
import com.bot.reminder.model.Reminder;
import com.bot.reminder.model.Task;
import com.bot.reminder.model.UserSettings;
import com.bot.reminder.repository.TaskRepository;
import com.bot.reminder.repository.ReminderRepository;
import com.bot.reminder.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    private final String botToken;
    private final String botUsername;
    private final TaskRepository taskRepository;
    private final ReminderRepository reminderRepository;
    private final GeminiService geminiService;
    private final UserSettingsRepository userSettingsRepository;
    private final PrayerTimeService prayerTimeService;

    public TelegramBotService(
            @Value("${TELEGRAM_BOT_TOKEN}") String botToken,
            @Value("${TELEGRAM_BOT_USERNAME}") String botUsername,
            TaskRepository taskRepository,
            ReminderRepository reminderRepository,
            GeminiService geminiService,
            UserSettingsRepository userSettingsRepository,
            PrayerTimeService prayerTimeService) {
        super(botToken);
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.taskRepository = taskRepository;
        this.reminderRepository = reminderRepository;
        this.geminiService = geminiService;
        this.userSettingsRepository = userSettingsRepository;
        this.prayerTimeService = prayerTimeService;
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
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();
            Long chatId = update.getMessage().getChatId();
            String userName = update.getMessage().getFrom().getFirstName();

            logger.info("Menerima pesan dari {}: '{}'", userName, messageText);

            if (messageText.startsWith("/start") || "ℹ️ Panduan Bot".equalsIgnoreCase(messageText)) {
                sendWelcomeMessage(chatId, userName);
            } else if (messageText.startsWith("/daftar") || "📋 Daftar Tugas".equalsIgnoreCase(messageText)) {
                handleDaftar(chatId);
            } else if (messageText.startsWith("/hapus")) {
                handleHapus(chatId, messageText);
            } else if (messageText.startsWith("/setlokasi") || "🕌 Atur Lokasi Shalat".equalsIgnoreCase(messageText)) {
                if ("🕌 Atur Lokasi Shalat".equalsIgnoreCase(messageText)) {
                    sendMessage(chatId, "📍 <b>Atur Lokasi Jadwal Shalat</b>\n\nSilakan ketik perintah berikut:\n<code>/setlokasi [nama kota]</code>\n\nContoh: <code>/setlokasi Jakarta</code>");
                } else {
                    handleSetLokasi(chatId, messageText);
                }
            } else if (messageText.startsWith("/jadwal") || "📅 Jadwal Hari Ini".equalsIgnoreCase(messageText)) {
                handleJadwal(chatId);
            } else if (messageText.startsWith("/settimezone")) {
                handleSetTimezone(chatId, messageText);
            } else if (messageText.startsWith("/silentmode")) {
                handleSilentMode(chatId, messageText);
            } else {
                handleNaturalLanguageTask(chatId, messageText);
            }
        }
    }

    private void sendWelcomeMessage(Long chatId, String name) {
        String welcome = "👋 Halo <b>" + name + "</b>!\n\n"
                + "Saya adalah <b>Task Reminder Bot</b> berbasis AI. "
                + "Saya bisa mencatat berbagai tugas & jadwal Anda menggunakan bahasa sehari-hari!\n\n"
                + "📋 <b>Fitur yang bisa Anda gunakan:</b>\n"
                + "1️⃣ <b>Tambah Tugas / Jadwal (AI)</b>\n"
                + "Cukup ketik pesan biasa, contoh:\n"
                + "• <i>\"kuliah Alpro setiap senin jam 9 pagi\"</i>\n"
                + "• <i>\"olahraga pagi besok jam 6\"</i>\n"
                + "• <i>\"tugas Alpro deadline tanggal 10 juni jam 12 siang\"</i>\n\n"
                + "2️⃣ <b>Lihat Jadwal Terdaftar</b>\n"
                + "• Klik tombol 📋 <b>Daftar Tugas</b> : Menampilkan list tugas aktif yang Anda buat.\n"
                + "• Klik tombol 📅 <b>Jadwal Hari Ini</b> : Menampilkan ringkasan jadwal shalat, tugas mendatang, dan reminder aktif.\n\n"
                + "3️⃣ <b>Atur Lokasi Jadwal Shalat</b>\n"
                + "Klik tombol 🕌 <b>Atur Lokasi Shalat</b> untuk mendapatkan instruksi pengaturan jadwal shalat otomatis.\n\n"
                + "4️⃣ <b>Pengaturan Zona Waktu & Mode Senyap</b>\n"
                + "• Ketik <code>/settimezone [WIB/WITA/WIT]</code> : Mengubah zona waktu Anda.\n"
                + "• Ketik <code>/silentmode [on/off]</code> : Mengaktifkan/mematikan notifikasi tanpa suara.\n\n"
                + "5️⃣ <b>Hapus Tugas</b>\n"
                + "Ketik perintah: <code>/hapus [nomor]</code>\n"
                + "Contoh: <code>/hapus 1</code> untuk menghapus tugas nomor 1 di daftar.\n\n"
                + "Silakan gunakan menu tombol di bawah atau ketik tugas pertama Anda! 🚀";

        sendMessageWithMenu(chatId, welcome);
    }

    private void handleDaftar(Long chatId) {
        List<Task> tasks = taskRepository.findByChatIdAndNotifiedFalseAndCategoryNotOrderByCreatedAtAsc(chatId, "IBADAH");

        if (tasks.isEmpty()) {
            sendMessage(chatId, "📭 <b>Daftar tugas Anda kosong!</b>\n\nKetik pesan seperti <i>\"ingatkan saya belajar Java besok malam jam 8\"</i> untuk menambah tugas baru.");
            return;
        }

        String tz = getUserTimezone(chatId);
        String tzLabel = getTimezoneLabel(tz);

        StringBuilder response = new StringBuilder("📝 <b>Daftar Tugas Aktif Anda:</b>\n\n");
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            String categoryEmoji = getCategoryEmoji(task.getCategory());
            response.append(i + 1).append(". ").append(categoryEmoji).append(" <b>").append(task.getDescription()).append("</b>\n");
            
            if (task.getNotes() != null && !task.getNotes().trim().isEmpty()) {
                response.append("   ℹ️ <i>Catatan: ").append(task.getNotes()).append("</i>\n");
            }
            
            if ("TUGAS".equalsIgnoreCase(task.getCategory())) {
                LocalDateTime userDeadline = toUserTime(task.getDeadlineTime(), chatId);
                response.append("   ⏰ <b>Deadline:</b> ").append(formatIndonesianDateTime(userDeadline, tzLabel)).append("\n");
            } else {
                LocalDateTime userReminder = toUserTime(task.getReminderTime(), chatId);
                response.append("   ⏰ <b>Waktu:</b> ").append(formatIndonesianDateTime(userReminder, tzLabel));
                if (!"NONE".equalsIgnoreCase(task.getRecurrence())) {
                    response.append(" (").append(getRecurrenceLabel(task.getRecurrence())).append(")");
                }
                response.append("\n");
            }
            response.append("\n");
        }

        sendMessage(chatId, response.toString());
    }

    private void handleHapus(Long chatId, String messageText) {
        String[] parts = messageText.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "⚠️ Format salah. Gunakan: <code>/hapus [nomor]</code>\nContoh: <code>/hapus 1</code>");
            return;
        }

        try {
            int index = Integer.parseInt(parts[1]) - 1;
            List<Task> tasks = taskRepository.findByChatIdAndNotifiedFalseAndCategoryNotOrderByCreatedAtAsc(chatId, "IBADAH");

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

    private void handleSetLokasi(Long chatId, String messageText) {
        String[] parts = messageText.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "⚠️ <b>Format salah.</b> Gunakan:\n<code>/setlokasi [nama kota / ID kota]</code>\n\nContoh:\n• <code>/setlokasi Jakarta</code>\n• <code>/setlokasi 1301</code>");
            return;
        }

        StringBuilder queryBuilder = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            queryBuilder.append(parts[i]).append(" ");
        }
        String query = queryBuilder.toString().trim();

        if (query.matches("\\d+")) {
            sendMessage(chatId, "🔍 <i>Memverifikasi ID kota " + query + "...</i>");
            String cityName = prayerTimeService.getCityNameById(query);
            if (cityName != null) {
                // Pertahankan timezone & silentmode lama jika ada
                UserSettings settings = userSettingsRepository.findById(chatId)
                        .orElse(new UserSettings(chatId, query, cityName, LocalDateTime.now()));
                settings.setCityId(query);
                settings.setCityName(cityName);
                userSettingsRepository.save(settings);

                prayerTimeService.scheduleDailyPrayersForUser(chatId, query, cityName, LocalDate.now());

                sendMessage(chatId, "✅ <b>Lokasi berhasil diatur!</b>\n📍 Kota: <b>" + cityName + "</b> (ID: " + query + ")\n\n<i>Jadwal shalat hari ini telah dijadwalkan otomatis! 🕌</i>");
            } else {
                sendMessage(chatId, "❌ <b>ID Kota tidak valid!</b> Pastikan Anda menyalin ID kota dengan benar dari daftar.");
            }
        } else {
            sendMessage(chatId, "🔍 <i>Mencari kota '" + query + "'...</i>");
            List<Map<String, String>> cities = prayerTimeService.searchCity(query);

            if (cities.isEmpty()) {
                sendMessage(chatId, "❌ <b>Kota tidak ditemukan!</b> Coba ketik nama kota dengan ejaan yang benar.");
            } else if (cities.size() == 1) {
                String cityId = cities.get(0).get("id");
                String cityName = cities.get(0).get("lokasi");

                UserSettings settings = userSettingsRepository.findById(chatId)
                        .orElse(new UserSettings(chatId, cityId, cityName, LocalDateTime.now()));
                settings.setCityId(cityId);
                settings.setCityName(cityName);
                userSettingsRepository.save(settings);

                prayerTimeService.scheduleDailyPrayersForUser(chatId, cityId, cityName, LocalDate.now());

                sendMessage(chatId, "✅ <b>Lokasi berhasil diatur!</b>\n📍 Kota: <b>" + cityName + "</b> (ID: " + cityId + ")\n\n<i>Jadwal shalat hari ini telah dijadwalkan otomatis! 🕌</i>");
            } else {
                StringBuilder sb = new StringBuilder("🔍 <b>Ditemukan beberapa kota yang cocok:</b>\n\n");
                for (Map<String, String> city : cities) {
                    sb.append("• <code>").append(city.get("id")).append("</code> - ").append(city.get("lokasi")).append("\n");
                }
                sb.append("\n💡 <i>Silakan salin ID kota pilihan Anda dan jalankan perintah:</i>\n<code>/setlokasi [id_kota]</code> (contoh: <code>/setlokasi ").append(cities.get(0).get("id")).append("</code>)");
                sendMessage(chatId, sb.toString());
            }
        }
    }

    private void handleJadwal(Long chatId) {
        StringBuilder response = new StringBuilder("📅 <b>RANGKUMAN JADWAL ANDA</b>\n");
        response.append("==============================\n\n");

        String userTz = getUserTimezone(chatId);
        String tzLabel = getTimezoneLabel(userTz);

        java.util.Optional<UserSettings> settingsOpt = userSettingsRepository.findById(chatId);
        if (settingsOpt.isEmpty()) {
            response.append("🕌 <b>Jadwal Shalat Hari Ini:</b>\n📍 <i>Lokasi belum diatur.</i>\n💡 Ketik <code>/setlokasi [nama kota]</code> untuk mengaktifkan pengingat shalat otomatis.\n\n");
        } else {
            UserSettings settings = settingsOpt.get();
            prayerTimeService.scheduleDailyPrayersForUser(chatId, settings.getCityId(), settings.getCityName(), LocalDate.now());

            Map<String, String> times = prayerTimeService.getPrayerTimes(settings.getCityId(), LocalDate.now());
            response.append("🕌 <b>Jadwal Shalat Hari Ini (").append(settings.getCityName()).append("):</b>\n");
            if (times.isEmpty()) {
                response.append("⚠️ <i>Gagal mengambil jadwal shalat dari API. Coba beberapa saat lagi.</i>\n\n");
            } else {
                response.append("• Subuh: <b>").append(times.get("subuh")).append(" ").append(tzLabel).append("</b>\n")
                        .append("• Dzuhur: <b>").append(times.get("dzuhur")).append(" ").append(tzLabel).append("</b>\n")
                        .append("• Ashar: <b>").append(times.get("ashar")).append(" ").append(tzLabel).append("</b>\n")
                        .append("• Maghrib: <b>").append(times.get("maghrib")).append(" ").append(tzLabel).append("</b>\n")
                        .append("• Isya: <b>").append(times.get("isya")).append(" ").append(tzLabel).append("</b>\n\n");
            }
        }

        List<Task> activeTasks = taskRepository.findByChatIdAndNotifiedFalse(chatId);

        List<Task> deadlineTasks = new ArrayList<>();
        List<Task> otherReminders = new ArrayList<>();

        for (Task t : activeTasks) {
            if ("TUGAS".equalsIgnoreCase(t.getCategory())) {
                deadlineTasks.add(t);
            } else if (!"IBADAH".equalsIgnoreCase(t.getCategory())) {
                otherReminders.add(t);
            }
        }

        deadlineTasks.sort((t1, t2) -> {
            if (t1.getDeadlineTime() == null) return 1;
            if (t2.getDeadlineTime() == null) return -1;
            return t1.getDeadlineTime().compareTo(t2.getDeadlineTime());
        });

        otherReminders.sort((t1, t2) -> {
            if (t1.getReminderTime() == null) return 1;
            if (t2.getReminderTime() == null) return -1;
            return t1.getReminderTime().compareTo(t2.getReminderTime());
        });

        response.append("📝 <b>Jadwal Tugas & Deadline Mendatang:</b>\n");
        if (deadlineTasks.isEmpty()) {
            response.append("<i>Tidak ada tugas mendatang. 🎉</i>\n\n");
        } else {
            for (int i = 0; i < deadlineTasks.size(); i++) {
                Task task = deadlineTasks.get(i);
                LocalDateTime userDeadline = toUserTime(task.getDeadlineTime(), chatId);
                response.append(i + 1).append(". <b>").append(task.getDescription()).append("</b>\n")
                        .append("   ⏰ Deadline: ").append(formatIndonesianDateTime(userDeadline, tzLabel)).append("\n");
                if (task.getNotes() != null && !task.getNotes().trim().isEmpty()) {
                    response.append("   ℹ️ <i>Catatan: ").append(task.getNotes()).append("</i>\n");
                }
                response.append("\n");
            }
        }

        response.append("🔔 <b>Pengingat Aktif Lainnya:</b>\n");
        if (otherReminders.isEmpty()) {
            response.append("<i>Tidak ada pengingat aktif.</i>\n");
        } else {
            for (int i = 0; i < otherReminders.size(); i++) {
                Task task = otherReminders.get(i);
                String catEmoji = getCategoryEmoji(task.getCategory());
                LocalDateTime userReminder = toUserTime(task.getReminderTime(), chatId);
                response.append(i + 1).append(". ").append(catEmoji).append(" <b>").append(task.getDescription()).append("</b>\n")
                        .append("   ⏰ Waktu: ").append(formatIndonesianDateTime(userReminder, tzLabel));
                if (!"NONE".equalsIgnoreCase(task.getRecurrence())) {
                    response.append(" (").append(getRecurrenceLabel(task.getRecurrence())).append(")");
                }
                response.append("\n");
                if (task.getNotes() != null && !task.getNotes().trim().isEmpty()) {
                    response.append("   ℹ️ <i>Catatan: ").append(task.getNotes()).append("</i>\n");
                }
                response.append("\n");
            }
        }

        sendMessage(chatId, response.toString());
    }

    private void handleSetTimezone(Long chatId, String messageText) {
        String[] parts = messageText.split("\\s+");
        if (parts.length < 2) {
            String currentTz = getUserTimezone(chatId);
            String tzLabel = getTimezoneLabel(currentTz);
            sendMessage(chatId, "⚙️ <b>Zona Waktu Anda:</b> <code>" + currentTz + "</code> (" + tzLabel + ")\n\n"
                    + "💡 Gunakan perintah berikut untuk mengubah:\n"
                    + "• <code>/settimezone WIB</code> (Asia/Jakarta)\n"
                    + "• <code>/settimezone WITA</code> (Asia/Makassar)\n"
                    + "• <code>/settimezone WIT</code> (Asia/Jayapura)\n"
                    + "• <code>/settimezone [ID Zona Waktu]</code> (contoh: <code>/settimezone Asia/Singapore</code>)");
            return;
        }

        String tzInput = parts[1].trim();
        String mappedZone = tzInput;

        if ("WIB".equalsIgnoreCase(tzInput)) {
            mappedZone = "Asia/Jakarta";
        } else if ("WITA".equalsIgnoreCase(tzInput)) {
            mappedZone = "Asia/Makassar";
        } else if ("WIT".equalsIgnoreCase(tzInput)) {
            mappedZone = "Asia/Jayapura";
        }

        try {
            ZoneId zoneId = ZoneId.of(mappedZone);
            String tzLabel = getTimezoneLabel(zoneId.getId());

            UserSettings settings = userSettingsRepository.findById(chatId)
                    .orElse(new UserSettings(chatId, "DEFAULT", "DEFAULT", LocalDateTime.now()));
            settings.setTimezone(zoneId.getId());
            userSettingsRepository.save(settings);

            sendMessage(chatId, "✅ <b>Zona waktu berhasil diubah!</b>\n⚙️ Zona Waktu: <b>" + zoneId.getId() + "</b> (" + tzLabel + ")\n⏰ Waktu Sekarang: <b>" + ZonedDateTime.now(zoneId).format(DateTimeFormatter.ofPattern("HH:mm")) + " " + tzLabel + "</b>");
        } catch (Exception e) {
            sendMessage(chatId, "❌ <b>Zona waktu tidak valid!</b> Gunakan format WIB, WITA, WIT, atau standard ID (contoh: <code>Asia/Jakarta</code>).");
        }
    }

    private void handleSilentMode(Long chatId, String messageText) {
        String[] parts = messageText.split("\\s+");
        if (parts.length < 2) {
            boolean currentMode = isSilentMode(chatId);
            sendMessage(chatId, "⚙️ <b>Mode Senyap Anda:</b> " + (currentMode ? "<b>ON 🔕 (Senyap)</b>" : "<b>OFF 🔔 (Suara)</b>") + "\n\n"
                    + "💡 Gunakan perintah berikut untuk mengubah:\n"
                    + "• <code>/silentmode on</code> (Mengaktifkan senyap)\n"
                    + "• <code>/silentmode off</code> (Mengaktifkan bersuara)");
            return;
        }

        String modeInput = parts[1].trim().toLowerCase();
        boolean setSilent;

        if ("on".equals(modeInput)) {
            setSilent = true;
        } else if ("off".equals(modeInput)) {
            setSilent = false;
        } else {
            sendMessage(chatId, "⚠️ Pilihan tidak valid. Gunakan <code>on</code> atau <code>off</code>.");
            return;
        }

        UserSettings settings = userSettingsRepository.findById(chatId)
                .orElse(new UserSettings(chatId, "DEFAULT", "DEFAULT", LocalDateTime.now()));
        settings.setSilentMode(setSilent);
        userSettingsRepository.save(settings);

        sendMessage(chatId, "✅ <b>Mode senyap berhasil diubah!</b>\n🔕 Mode Senyap: " + (setSilent ? "<b>ON (Notifikasi Tanpa Suara)</b>" : "<b>OFF (Notifikasi Bersuara)</b>"));
    }

    private void handleNaturalLanguageTask(Long chatId, String messageText) {
        String cleanedText = messageText;
        if (messageText.toLowerCase().startsWith("/tambah")) {
            cleanedText = messageText.substring(7).trim();
        }

        sendMessage(chatId, "🤖 <i>Sedang menganalisis tugas Anda...</i>");

        // Ambil waktu user sekarang untuk referensi Gemini
        String userTz = getUserTimezone(chatId);
        String tzLabel = getTimezoneLabel(userTz);
        ZonedDateTime userNowZoned = ZonedDateTime.now(ZoneId.of(userTz));
        LocalDateTime userNow = userNowZoned.toLocalDateTime();

        GeminiTaskResponse response = geminiService.parseTask(cleanedText, userNow);

        if (response == null || "FAILED".equals(response.getStatus()) || response.getTask() == null || response.getDatetime() == null) {
            String fallbackMessage = "❌ <b>Bot tidak memahami tugas atau waktu pengingat.</b>\n\n"
                    + "Pastikan Anda menyertakan deskripsi tugas dan kapan harus diingatkan secara jelas.\n"
                    + "💡 <b>Contoh yang benar:</b>\n"
                    + "• <i>\"ingatkan saya belajar java besok jam 7 malam\"</i>\n"
                    + "• <i>\"kuliah Sistem Operasi setiap senin jam 9 pagi\"</i>\n"
                    + "• <i>\"tugas Alpro deadline tanggal 10 juni jam 12 siang\"</i>";
            sendMessage(chatId, fallbackMessage);
            return;
        }

        try {
            String datetimeStr = response.getDatetime();
            if (datetimeStr != null && datetimeStr.length() > 16) {
                datetimeStr = datetimeStr.substring(0, 16);
            }
            
            // Waktu target dari Gemini adalah waktu lokal user
            LocalDateTime userTargetTime = LocalDateTime.parse(datetimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            if (userTargetTime.isBefore(userNow)) {
                sendMessage(chatId, "⚠️ <b>Waktu pengingat sudah lewat!</b> Pastikan Anda menentukan waktu di masa depan.");
                return;
            }

            // Konversi dari waktu lokal user ke waktu server untuk disimpan ke database
            LocalDateTime targetTime = toServerTime(userTargetTime, chatId);

            Task task = new Task();
            task.setChatId(chatId);
            task.setDescription(response.getTask());
            task.setNotes(response.getNotes());
            task.setCategory(response.getCategory() != null ? response.getCategory() : "UMUM");
            task.setRecurrence(response.getRecurrence() != null ? response.getRecurrence() : "NONE");
            task.setNotified(false);

            int scheduledRemindersCount = 0;

            if ("TUGAS".equalsIgnoreCase(task.getCategory())) {
                task.setDeadlineTime(targetTime);
                task.setReminderTime(targetTime);

                // Hitung reminder-reminder bergradasi di waktu lokal user, lalu konversi ke waktu server sebelum disave
                LocalDateTime nowServer = LocalDateTime.now();

                // 1. H-3
                LocalDateTime userTH3 = userTargetTime.minusDays(3);
                LocalDateTime serverTH3 = toServerTime(userTH3, chatId);
                if (serverTH3.isAfter(nowServer)) {
                    task.addReminder(new Reminder(task, serverTH3, "H3", null));
                    scheduledRemindersCount++;
                }

                // 2. H-3 Jam
                LocalDateTime userT3H = userTargetTime.minusHours(3);
                LocalDateTime serverT3H = toServerTime(userT3H, chatId);

                // 3. Harian
                LocalDate startDay = (userTH3.isBefore(userNow) ? userNow : userTH3).toLocalDate();
                LocalDate endDay = userTargetTime.toLocalDate();

                for (LocalDate date = startDay; !date.isAfter(endDay); date = date.plusDays(1)) {
                    LocalDateTime morningTimeUser = LocalDateTime.of(date, LocalTime.of(9, 0));
                    LocalDateTime eveningTimeUser = LocalDateTime.of(date, LocalTime.of(19, 0));

                    LocalDateTime morningTimeServer = toServerTime(morningTimeUser, chatId);
                    LocalDateTime eveningTimeServer = toServerTime(eveningTimeUser, chatId);

                    if (morningTimeServer.isAfter(nowServer) && morningTimeServer.isBefore(serverT3H) && (morningTimeUser.isAfter(userTH3) || morningTimeUser.isEqual(userTH3))) {
                        task.addReminder(new Reminder(task, morningTimeServer, "DAILY", null));
                        scheduledRemindersCount++;
                    }

                    if (eveningTimeServer.isAfter(nowServer) && eveningTimeServer.isBefore(serverT3H) && (eveningTimeUser.isAfter(userTH3) || eveningTimeUser.isEqual(userTH3))) {
                        task.addReminder(new Reminder(task, eveningTimeServer, "DAILY", null));
                        scheduledRemindersCount++;
                    }
                }

                // H-3 Jam save
                if (serverT3H.isAfter(nowServer)) {
                    task.addReminder(new Reminder(task, serverT3H, "THREE_HOURS", null));
                    scheduledRemindersCount++;
                }

                if (scheduledRemindersCount == 0) {
                    task.addReminder(new Reminder(task, targetTime, "DEFAULT", null));
                    scheduledRemindersCount++;
                }

            } else {
                task.setReminderTime(targetTime);
                task.addReminder(new Reminder(task, targetTime, "DEFAULT", null));
                scheduledRemindersCount = 1;
            }

            taskRepository.save(task);

            StringBuilder successMessage = new StringBuilder("✅ <b>Tugas berhasil ditambahkan!</b>\n\n"
                    + "📝 <b>Tugas:</b> " + response.getTask() + "\n"
                    + "🗂️ <b>Kategori:</b> " + task.getCategory() + "\n");
            
            if (!"NONE".equalsIgnoreCase(task.getRecurrence())) {
                successMessage.append("🔁 <b>Pengulangan:</b> ").append(getRecurrenceLabel(task.getRecurrence())).append("\n");
            }
            
            if (response.getNotes() != null && !response.getNotes().trim().isEmpty()) {
                successMessage.append("ℹ️ <b>Catatan:</b> ").append(response.getNotes()).append("\n");
            }
            
            if ("TUGAS".equalsIgnoreCase(task.getCategory())) {
                successMessage.append("⏰ <b>Deadline:</b> ").append(formatIndonesianDateTime(userTargetTime, tzLabel)).append("\n")
                               .append("🔔 <b>Total Pengingat Terjadwal:</b> ").append(scheduledRemindersCount).append(" notifikasi (H-3, harian 9:00/19:00, & H-3 jam)");
            } else {
                successMessage.append("⏰ <b>Waktu:</b> ").append(formatIndonesianDateTime(userTargetTime, tzLabel));
            }

            sendMessage(chatId, successMessage.toString());
        } catch (Exception e) {
            logger.error("Gagal menyimpan tugas", e);
            sendMessage(chatId, "❌ Terjadi kesalahan sistem saat menyimpan tugas Anda.");
        }
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML")
                .disableNotification(isSilentMode(chatId))
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Gagal mengirim pesan ke chatId {}", chatId, e);
        }
    }

    public void sendMessageWithMenu(Long chatId, String text) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📋 Daftar Tugas"));
        row1.add(new KeyboardButton("📅 Jadwal Hari Ini"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🕌 Atur Lokasi Shalat"));
        row2.add(new KeyboardButton("ℹ️ Panduan Bot"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboardMarkup.setKeyboard(keyboard);

        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML")
                .replyMarkup(keyboardMarkup)
                .disableNotification(isSilentMode(chatId))
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Gagal mengirim pesan dengan menu keyboard ke chatId {}", chatId, e);
        }
    }

    public void sendReminderWithButtons(Long chatId, String text, Long reminderId, boolean includeButtons) {
        SendMessage.SendMessageBuilder messageBuilder = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML")
                .disableNotification(isSilentMode(chatId));

        if (includeButtons) {
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton btnDone = InlineKeyboardButton.builder()
                    .text("✅ Selesai")
                    .callbackData("done_" + reminderId)
                    .build();
            row1.add(btnDone);

            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton btnSnooze15 = InlineKeyboardButton.builder()
                    .text("⏳ Tunda 15 Mnt")
                    .callbackData("snooze_15_" + reminderId)
                    .build();
            InlineKeyboardButton btnSnooze60 = InlineKeyboardButton.builder()
                    .text("⏳ Tunda 1 Jam")
                    .callbackData("snooze_60_" + reminderId)
                    .build();
            row2.add(btnSnooze15);
            row2.add(btnSnooze60);

            rowsInline.add(row1);
            rowsInline.add(row2);
            markupInline.setKeyboard(rowsInline);

            messageBuilder.replyMarkup(markupInline);
        }

        try {
            execute(messageBuilder.build());
        } catch (TelegramApiException e) {
            logger.error("Gagal mengirim reminder dengan tombol ke chatId {}", chatId, e);
        }
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackQueryId = update.getCallbackQuery().getId();

        logger.info("Menerima callback query: '{}' dari chatId {}", callbackData, chatId);

        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .build();
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Gagal menjawab callback query", e);
        }

        try {
            if (callbackData.startsWith("done_")) {
                Long reminderId = Long.parseLong(callbackData.substring(5));
                java.util.Optional<Reminder> reminderOpt = reminderRepository.findById(reminderId);

                if (reminderOpt.isEmpty()) {
                    sendMessage(chatId, "⚠️ Pengingat tidak ditemukan atau sudah dihapus.");
                    return;
                }

                Reminder reminder = reminderOpt.get();
                Task task = reminder.getTask();

                task.setNotified(true);
                for (Reminder r : task.getReminders()) {
                    r.setSent(true);
                }
                taskRepository.save(task);

                String updatedText = "✅ <b>TUGAS SELESAI!</b>\n\n"
                        + "📝 <b>" + task.getDescription() + "</b>\n"
                        + "Tugas telah ditandai selesai! Terima kasih sudah menyelesaikannya. 🎉";

                EditMessageText editMessage = EditMessageText.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .text(updatedText)
                        .parseMode("HTML")
                        .replyMarkup(null)
                        .build();
                execute(editMessage);

            } else if (callbackData.startsWith("snooze_")) {
                String[] parts = callbackData.split("_");
                int minutes = Integer.parseInt(parts[1]);
                Long reminderId = Long.parseLong(parts[2]);

                java.util.Optional<Reminder> reminderOpt = reminderRepository.findById(reminderId);

                if (reminderOpt.isEmpty()) {
                    sendMessage(chatId, "⚠️ Pengingat tidak ditemukan atau sudah dihapus.");
                    return;
                }

                Reminder oldReminder = reminderOpt.get();
                Task task = oldReminder.getTask();

                // Hitung waktu tunda baru berdasarkan zona waktu pengguna
                String userTz = getUserTimezone(chatId);
                ZoneId userZone = ZoneId.of(userTz);
                ZonedDateTime userNowZoned = ZonedDateTime.now(userZone);
                ZonedDateTime newReminderTimeUser = userNowZoned.plusMinutes(minutes);
                
                // Konversikan kembali ke waktu server untuk save
                LocalDateTime newReminderTimeServer = newReminderTimeUser.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

                Reminder newReminder = new Reminder(
                        task,
                        newReminderTimeServer,
                        oldReminder.getMessageType(),
                        oldReminder.getCustomMessage()
                );
                
                task.setNotified(false);
                if (!"TUGAS".equalsIgnoreCase(task.getCategory())) {
                    task.setReminderTime(newReminderTimeServer);
                }
                task.addReminder(newReminder);
                taskRepository.save(task);

                String tzLabel = getTimezoneLabel(userTz);
                String updatedText = "⏳ <b>PENGINGAT DITUNDA!</b>\n\n"
                        + "📝 <b>" + task.getDescription() + "</b>\n"
                        + "Pengingat ditunda selama <b>" + minutes + " menit</b>.\n"
                        + "Akan diingatkan kembali pada jam: <b>" + newReminderTimeUser.format(DateTimeFormatter.ofPattern("HH:mm")) + " " + tzLabel + "</b>.";

                EditMessageText editMessage = EditMessageText.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .text(updatedText)
                        .parseMode("HTML")
                        .replyMarkup(null)
                        .build();
                execute(editMessage);
            }
        } catch (Exception e) {
            logger.error("Gagal menangani callback query", e);
            sendMessage(chatId, "❌ Terjadi kesalahan saat memproses aksi tombol.");
        }
    }

    private String getUserTimezone(Long chatId) {
        return userSettingsRepository.findById(chatId)
                .map(UserSettings::getTimezone)
                .orElse("Asia/Jakarta");
    }

    private boolean isSilentMode(Long chatId) {
        return userSettingsRepository.findById(chatId)
                .map(UserSettings::isSilentMode)
                .orElse(false);
    }

    private LocalDateTime toUserTime(LocalDateTime serverTime, Long chatId) {
        if (serverTime == null) return null;
        String tzStr = getUserTimezone(chatId);
        ZoneId userZone = ZoneId.of(tzStr);
        ZonedDateTime serverZonedDateTime = serverTime.atZone(ZoneId.systemDefault());
        return serverZonedDateTime.withZoneSameInstant(userZone).toLocalDateTime();
    }

    private LocalDateTime toServerTime(LocalDateTime userTime, Long chatId) {
        if (userTime == null) return null;
        String tzStr = getUserTimezone(chatId);
        ZoneId userZone = ZoneId.of(tzStr);
        ZonedDateTime userZonedDateTime = userTime.atZone(userZone);
        return userZonedDateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String getTimezoneLabel(String timezone) {
        if (timezone == null) return "WIB";
        switch (timezone) {
            case "Asia/Jakarta": return "WIB";
            case "Asia/Makassar": return "WITA";
            case "Asia/Jayapura": return "WIT";
            default:
                try {
                    ZoneId zoneId = ZoneId.of(timezone);
                    return zoneId.getDisplayName(java.time.format.TextStyle.SHORT, new java.util.Locale("id"));
                } catch (Exception e) {
                    return "WIB";
                }
        }
    }

    private String getCategoryEmoji(String category) {
        if (category == null) return "🔔";
        switch (category.toUpperCase()) {
            case "KULIAH": return "🎓";
            case "OLAHRAGA": return "🏃";
            case "IBADAH": return "🕌";
            case "TUGAS": return "📝";
            case "BELAJAR": return "📚";
            default: return "🔔";
        }
    }

    private String getRecurrenceLabel(String recurrence) {
        if (recurrence == null) return "";
        switch (recurrence.toUpperCase()) {
            case "DAILY": return "Setiap Hari";
            case "WEEKLY": return "Setiap Minggu";
            default: return "";
        }
    }

    private String formatIndonesianDateTime(LocalDateTime dateTime, String tzLabel) {
        if (dateTime == null) return "-";
        String[] months = {
                "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        };
        return dateTime.getDayOfMonth() + " " + months[dateTime.getMonthValue() - 1] + " " + dateTime.getYear() + ", "
                + String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute()) + " " + tzLabel;
    }
}
