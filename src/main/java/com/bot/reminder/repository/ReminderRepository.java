package com.bot.reminder.repository;

import com.bot.reminder.model.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    // Mengambil semua pengingat yang belum dikirim dan sudah jatuh tempo
    List<Reminder> findBySentFalseAndReminderTimeLessThanEqual(LocalDateTime time);

    // Mengambil semua pengingat untuk tugas tertentu yang belum dikirim
    List<Reminder> findByTask_IdAndSentFalse(Long taskId);

    // Mengecek apakah pengingat shalat sudah dijadwalkan untuk user pada hari tertentu
    boolean existsByTask_ChatIdAndTask_CategoryAndReminderTimeBetween(Long chatId, String category, LocalDateTime start, LocalDateTime end);
}
