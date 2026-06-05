package com.bot.reminder.dto;

public class GeminiTaskResponse {
    private String task;       // Deskripsi tugas
    private String notes;      // Detail / catatan tambahan tugas
    private String datetime;   // Tanggal & waktu format "yyyy-MM-dd HH:mm"
    private String status;     // "SUCCESS" jika berhasil mem-parsing, "FAILED" jika tidak
    private String category;   // Kategori tugas: "KULIAH", "OLAHRAGA", "IBADAH", "TUGAS", "BELAJAR", "UMUM"
    private String recurrence; // Pengulangan: "NONE", "DAILY", "WEEKLY"

    // Default Constructor
    public GeminiTaskResponse() {
    }

    // Full Constructor
    public GeminiTaskResponse(String task, String notes, String datetime, String status, String category, String recurrence) {
        this.task = task;
        this.notes = notes;
        this.datetime = datetime;
        this.status = status;
        this.category = category;
        this.recurrence = recurrence;
    }

    // Getters and Setters
    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    // Manual Builder Pattern Implementation to maintain compatibility
    public static class GeminiTaskResponseBuilder {
        private String task;
        private String notes;
        private String datetime;
        private String status;
        private String category;
        private String recurrence;

        public GeminiTaskResponseBuilder task(String task) {
            this.task = task;
            return this;
        }

        public GeminiTaskResponseBuilder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public GeminiTaskResponseBuilder datetime(String datetime) {
            this.datetime = datetime;
            return this;
        }

        public GeminiTaskResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public GeminiTaskResponseBuilder category(String category) {
            this.category = category;
            return this;
        }

        public GeminiTaskResponseBuilder recurrence(String recurrence) {
            this.recurrence = recurrence;
            return this;
        }

        public GeminiTaskResponse build() {
            return new GeminiTaskResponse(task, notes, datetime, status, category, recurrence);
        }
    }

    public static GeminiTaskResponseBuilder builder() {
        return new GeminiTaskResponseBuilder();
    }
}
