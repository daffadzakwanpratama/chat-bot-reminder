package com.bot.reminder.service;

import com.bot.reminder.model.Task;
import com.bot.reminder.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final TaskRepository taskRepository;
    private final TelegramBotService telegramBotService;

    // Konstruktor manual tanpa Lombok @RequiredArgsConstructor
    public SchedulerService(TaskRepository taskRepository, TelegramBotService telegramBotService) {
        this.taskRepository = taskRepository;
        this.telegramBotService = telegramBotService;
    }

    /**
     * Berjalan otomatis di background setiap 30 detik (30.000 ms).
     * Memindai database untuk mendeteksi tugas yang jatuh tempo dan mengirim pengingat.
     */
    @Scheduled(fixedRate = 30000)
    public void checkAndSendReminders() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("Scheduler berjalan mengecek deadline tugas pada: {}", now);

        // Mengambil semua tugas aktif yang waktunya kurang dari atau sama dengan sekarang
        List<Task> pendingTasks = taskRepository.findByNotifiedFalseAndReminderTimeLessThanEqual(now);

        if (pendingTasks.isEmpty()) {
            return;
        }

        log.info("Menemukan {} tugas yang harus diingatkan!", pendingTasks.size());

        for (Task task : pendingTasks) {
            try {
                // Menyusun pesan pengingat yang indah dengan emoji
                String reminderMessage = "🔔 <b>PENGINGAT TUGAS!</b>\n\n"
                        + "Jangan lupa untuk mengerjakan:\n"
                        + "📝 <b>" + task.getDescription() + "</b>\n\n"
                        + "<i>Semangat menyelesaikan tugasmu! 💪</i>";

                // Mengirim pesan ke chat ID Telegram user
                telegramBotService.sendMessage(task.getChatId(), reminderMessage);

                // Menandai tugas sebagai 'sudah diingatkan' di database
                task.setNotified(true);
                taskRepository.save(task);

                log.info("Berhasil mengirim pengingat tugas ID {} ke chatId {}", task.getId(), task.getChatId());
            } catch (Exception e) {
                log.error("Gagal memproses pengingat untuk tugas ID {}", task.getId(), e);
            }
        }
    }
}
