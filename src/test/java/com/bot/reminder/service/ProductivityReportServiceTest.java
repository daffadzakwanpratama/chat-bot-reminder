package com.bot.reminder.service;

import com.bot.reminder.model.Task;
import com.bot.reminder.model.UserSettings;
import com.bot.reminder.repository.TaskRepository;
import com.bot.reminder.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class ProductivityReportServiceTest {

    private TaskRepository taskRepository;
    private UserSettingsRepository userSettingsRepository;
    private ProductivityReportService reportService;

    @BeforeEach
    public void setUp() {
        taskRepository = Mockito.mock(TaskRepository.class);
        userSettingsRepository = Mockito.mock(UserSettingsRepository.class);
        reportService = new ProductivityReportService(taskRepository, userSettingsRepository);
    }

    @Test
    public void testGenerateWeeklyReport_NoTasks() {
        Long chatId = 123L;
        UserSettings settings = new UserSettings(chatId, "1301", "Jakarta", LocalDateTime.now());
        settings.setTimezone("Asia/Jakarta");

        when(userSettingsRepository.findById(chatId)).thenReturn(Optional.of(settings));
        when(taskRepository.findByChatIdAndCreatedAtAfter(eq(chatId), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        String report = reportService.generateWeeklyReport(chatId);

        assertNotNull(report);
        assertTrue(report.contains("Total Tugas Dibuat: <b>0</b>"));
        assertTrue(report.contains("Skor Produktivitas: <b>0%</b>"));
        assertTrue(report.contains("Tenang dan santai!"));
    }

    @Test
    public void testGenerateWeeklyReport_WithTasks() {
        Long chatId = 123L;
        UserSettings settings = new UserSettings(chatId, "1301", "Jakarta", LocalDateTime.now());
        settings.setTimezone("Asia/Jakarta");

        Task task1 = new Task(1L, chatId, "Belajar Java", null, null, true, LocalDateTime.now(), "BELAJAR", "NONE", null, true, LocalDateTime.now());
        Task task2 = new Task(2L, chatId, "Olahraga Sore", null, null, true, LocalDateTime.now(), "OLAHRAGA", "NONE", null, false, null);
        Task task3 = new Task(3L, chatId, "Shalat Dzuhur", null, null, true, LocalDateTime.now(), "IBADAH", "NONE", null, true, LocalDateTime.now()); // should be ignored

        when(userSettingsRepository.findById(chatId)).thenReturn(Optional.of(settings));
        when(taskRepository.findByChatIdAndCreatedAtAfter(eq(chatId), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(task1, task2, task3));

        String report = reportService.generateWeeklyReport(chatId);

        assertNotNull(report);
        // Excludes IBADAH: total non-IBADAH tasks is 2 (task1, task2)
        assertTrue(report.contains("Total Tugas Dibuat: <b>2</b>"));
        // Task 1 is completed, Task 2 is not completed. So 1 out of 2 is 50%
        assertTrue(report.contains("Tugas Selesai: <b>1</b>"));
        assertTrue(report.contains("Skor Produktivitas: <b>50%</b>"));
        assertTrue(report.contains("Kerja bagus!"));
    }
}
