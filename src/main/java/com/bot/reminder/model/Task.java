package com.bot.reminder.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "reminder_time", nullable = true)
    private LocalDateTime reminderTime;

    @Column(name = "notes")
    private String notes;

    @Column(name = "is_notified", nullable = false)
    private boolean notified = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "category", nullable = false)
    private String category = "UMUM"; // e.g. "KULIAH", "OLAHRAGA", "IBADAH", "TUGAS", "BELAJAR", "UMUM"

    @Column(name = "recurrence", nullable = false)
    private String recurrence = "NONE"; // e.g. "NONE", "DAILY", "WEEKLY"

    @Column(name = "deadline_time", nullable = true)
    private LocalDateTime deadlineTime;

    @Column(name = "is_completed", nullable = true)
    private Boolean completed = false;

    @Column(name = "completed_at", nullable = true)
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Reminder> reminders = new ArrayList<>();

    // Default Constructor (Required by JPA)
    public Task() {
    }

    // Full Constructor
    public Task(Long id, Long chatId, String description, String notes, LocalDateTime reminderTime, boolean notified, 
                LocalDateTime createdAt, String category, String recurrence, LocalDateTime deadlineTime, Boolean completed, LocalDateTime completedAt) {
        this.id = id;
        this.chatId = chatId;
        this.description = description;
        this.notes = notes;
        this.reminderTime = reminderTime;
        this.notified = notified;
        this.createdAt = createdAt;
        this.category = category;
        this.recurrence = recurrence;
        this.deadlineTime = deadlineTime;
        this.completed = completed != null ? completed : false;
        this.completedAt = completedAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Helper to add reminder
    public void addReminder(Reminder reminder) {
        reminders.add(reminder);
        reminder.setTask(this);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(LocalDateTime reminderTime) {
        this.reminderTime = reminderTime;
    }


    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(String recurrence) {
        this.recurrence = recurrence;
    }

    public LocalDateTime getDeadlineTime() {
        return deadlineTime;
    }

    public void setDeadlineTime(LocalDateTime deadlineTime) {
        this.deadlineTime = deadlineTime;
    }

    public boolean isCompleted() {
        return completed != null ? completed : false;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<Reminder> getReminders() {
        return reminders;
    }

    public void setReminders(List<Reminder> reminders) {
        this.reminders = reminders;
    }
}
