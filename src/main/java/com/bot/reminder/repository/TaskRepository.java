package com.bot.reminder.repository;

import com.bot.reminder.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // Mengambil semua tugas aktif (belum diingatkan) milik user tertentu, diurutkan dari waktu terdekat
    List<Task> findByChatIdAndNotifiedFalseOrderByReminderTimeAsc(Long chatId);

    // Mengambil semua tugas aktif yang waktu pengingatnya sudah lewat atau sama dengan waktu sekarang
    List<Task> findByNotifiedFalseAndReminderTimeLessThanEqual(LocalDateTime time);

    // Mengambil semua tugas aktif milik user tertentu kecuali kategori tertentu, diurutkan berdasarkan pembuatan
    List<Task> findByChatIdAndNotifiedFalseAndCategoryNotOrderByCreatedAtAsc(Long chatId, String excludeCategory);

    // Mengambil seluruh tugas aktif milik user tertentu
    List<Task> findByChatIdAndNotifiedFalse(Long chatId);
}
