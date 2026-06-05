package com.bot.reminder.service;

import com.bot.reminder.model.Reminder;
import com.bot.reminder.model.Task;
import com.bot.reminder.model.UserSettings;
import com.bot.reminder.repository.ReminderRepository;
import com.bot.reminder.repository.TaskRepository;
import com.bot.reminder.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final TaskRepository taskRepository;
    private final ReminderRepository reminderRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final TelegramBotService telegramBotService;
    private final PrayerTimeService prayerTimeService;

    public SchedulerService(TaskRepository taskRepository,
                            ReminderRepository reminderRepository,
                            UserSettingsRepository userSettingsRepository,
                            TelegramBotService telegramBotService,
                            PrayerTimeService prayerTimeService) {
        this.taskRepository = taskRepository;
        this.reminderRepository = reminderRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.telegramBotService = telegramBotService;
        this.prayerTimeService = prayerTimeService;
    }

    /**
     * Berjalan otomatis di background setiap 30 detik (30.000 ms).
     * Memindai database untuk mendeteksi pengingat (Reminder) yang jatuh tempo.
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void checkAndSendReminders() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("Scheduler berjalan mengecek deadline tugas pada: {}", now);

        // Mengambil semua pengingat yang belum terkirim dan waktunya sudah lewat/sama dengan sekarang
        List<Reminder> pendingReminders = reminderRepository.findBySentFalseAndReminderTimeLessThanEqual(now);

        if (pendingReminders.isEmpty()) {
            return;
        }

        log.info("Menemukan {} pengingat yang harus dikirim!", pendingReminders.size());

        for (Reminder reminder : pendingReminders) {
            try {
                Task task = reminder.getTask();
                String message = buildReminderMessage(reminder);

                // Mengirim pesan ke chat ID Telegram user
                telegramBotService.sendMessage(task.getChatId(), message);

                // Menandai pengingat ini sudah terkirim
                reminder.setSent(true);
                reminderRepository.save(reminder);

                // Handle pengulangan (Recurrence) jika ada
                if (!"NONE".equalsIgnoreCase(task.getRecurrence())) {
                    LocalDateTime nextReminderTime = calculateNextReminderTime(reminder.getReminderTime(), task.getRecurrence());
                    Reminder newReminder = new Reminder(
                            task,
                            nextReminderTime,
                            reminder.getMessageType(),
                            reminder.getCustomMessage()
                    );
                    reminderRepository.save(newReminder);
                    log.info("Menjadwalkan ulang pengingat berulang ({}) untuk tugas ID {} pada {}",
                            task.getRecurrence(), task.getId(), nextReminderTime);
                } else {
                    // Cek jika tidak ada pengingat lain yang tersisa untuk tugas ini
                    List<Reminder> remainingReminders = reminderRepository.findByTask_IdAndSentFalse(task.getId());
                    if (remainingReminders.isEmpty()) {
                        task.setNotified(true);
                        taskRepository.save(task);
                        log.info("Tugas ID {} ditandai selesai/notified karena seluruh pengingat telah dikirim.", task.getId());
                    }
                }

                log.info("Berhasil mengirim pengingat ID {} untuk tugas ID {} ke chatId {}",
                        reminder.getId(), task.getId(), task.getChatId());
            } catch (Exception e) {
                log.error("Gagal memproses pengingat ID {}", reminder.getId(), e);
            }
        }
    }

    /**
     * Menjadwalkan pengingat shalat harian untuk seluruh user yang lokasinya tersimpan.
     * Berjalan setiap hari pada pukul 00:05 pagi.
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void scheduleDailyPrayersForAllUsers() {
        log.info("Memulai scheduler pengambilan jadwal shalat harian untuk seluruh user...");
        List<UserSettings> allSettings = userSettingsRepository.findAll();
        LocalDate today = LocalDate.now();

        for (UserSettings settings : allSettings) {
            try {
                prayerTimeService.scheduleDailyPrayersForUser(
                        settings.getChatId(),
                        settings.getCityId(),
                        settings.getCityName(),
                        today
                );
            } catch (Exception e) {
                log.error("Gagal menjadwalkan shalat harian untuk chatId: {}", settings.getChatId(), e);
            }
        }
    }

    /**
     * Menyusun pesan pengingat berdasarkan kategori dan tipe pengingat.
     */
    private String buildReminderMessage(Reminder reminder) {
        if (reminder.getCustomMessage() != null && !reminder.getCustomMessage().trim().isEmpty()) {
            return reminder.getCustomMessage();
        }

        Task task = reminder.getTask();
        String category = task.getCategory() != null ? task.getCategory().toUpperCase() : "UMUM";
        String desc = task.getDescription();
        String notesStr = (task.getNotes() != null && !task.getNotes().trim().isEmpty())
                ? "\nℹ️ <i>Catatan: " + task.getNotes() + "</i>"
                : "";

        switch (category) {
            case "KULIAH":
                return String.format("🎓 <b>JADWAL KULIAH!</b>\n\nJangan lupa kelas:\n📝 <b>%s</b>%s\n\n<i>Semangat belajarnya! 🚀</i>", desc, notesStr);
            case "OLAHRAGA":
                return String.format("🏃 <b>WAKTUNYA OLAHRAGA!</b>\n\nJangan lupa untuk:\n📝 <b>%s</b>%s\n\n<i>Yuk olahraga agar tubuh tetap bugar dan sehat! 💪</i>", desc, notesStr);
            case "BELAJAR":
                return String.format("📚 <b>WAKTUNYA BELAJAR!</b>\n\nJangan lupa untuk:\n📝 <b>%s</b>%s\n\n<i>Fokus belajar dan semoga sukses materinya! 📖</i>", desc, notesStr);
            case "TUGAS":
                String deadlineStr = formatIndonesianDateTime(task.getDeadlineTime());
                if ("H3".equalsIgnoreCase(reminder.getMessageType())) {
                    return String.format("🚨 <b>PENGINGAT TUGAS (H-3)!</b>\n\nDeadline tugas semakin dekat, jangan lupa dicicil:\n📝 <b>%s</b>%s\n\n⏰ <b>Deadline:</b> %s\n\n<i>Masih ada waktu 3 hari lagi. Ayo dikerjakan! ✍️</i>", desc, notesStr, deadlineStr);
                } else if ("THREE_HOURS".equalsIgnoreCase(reminder.getMessageType())) {
                    return String.format("🚨 <b>PENGINGAT CRITICAL (3 Jam Lagi)!</b>\n\nTugas ini harus segera dikumpulkan:\n📝 <b>%s</b>%s\n\n⏰ <b>Deadline:</b> %s\n\n<i>Waktu tinggal 3 jam lagi! Ayo buruan diselesaikan dan dikumpul! ⏰🔥</i>", desc, notesStr, deadlineStr);
                } else {
                    return String.format("🔔 <b>PENGINGAT HARIAN TUGAS!</b>\n\nJangan lupa untuk mengerjakan:\n📝 <b>%s</b>%s\n\n⏰ <b>Deadline:</b> %s\n\n<i>Semangat menyelesaikan tugasnya, jangan tunda-tunda lagi! 💪</i>", desc, notesStr, deadlineStr);
                }
            default:
                return String.format("🔔 <b>PENGINGAT TUGAS!</b>\n\nJangan lupa untuk:\n📝 <b>%s</b>%s\n\n<i>Semangat menyelesaikan tugasmu! 💪</i>", desc, notesStr);
        }
    }

    /**
     * Menghitung waktu reminder berikutnya berdasarkan tipe pengulangan.
     */
    private LocalDateTime calculateNextReminderTime(LocalDateTime current, String recurrence) {
        if ("DAILY".equalsIgnoreCase(recurrence)) {
            return current.plusDays(1);
        } else if ("WEEKLY".equalsIgnoreCase(recurrence)) {
            return current.plusWeeks(1);
        }
        return current;
    }

    /**
     * Memformat objek LocalDateTime menjadi format Bahasa Indonesia.
     */
    private String formatIndonesianDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "-";
        String[] months = {
                "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        };
        return dateTime.getDayOfMonth() + " " + months[dateTime.getMonthValue() - 1] + " " + dateTime.getYear() + ", "
                + String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute()) + " WIB";
    }
}
