package com.bot.reminder.dto;

public class GeminiTaskResponse {
    private String task;       // Deskripsi tugas
    private String datetime;   // Tanggal & waktu format "yyyy-MM-dd HH:mm"
    private String status;     // "SUCCESS" jika berhasil mem-parsing, "FAILED" jika tidak

    // Default Constructor
    public GeminiTaskResponse() {
    }

    // Full Constructor
    public GeminiTaskResponse(String task, String datetime, String status) {
        this.task = task;
        this.datetime = datetime;
        this.status = status;
    }

    // Getters and Setters
    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
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

    // Manual Builder Pattern Implementation to maintain compatibility
    public static class GeminiTaskResponseBuilder {
        private String task;
        private String datetime;
        private String status;

        public GeminiTaskResponseBuilder task(String task) {
            this.task = task;
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

        public GeminiTaskResponse build() {
            return new GeminiTaskResponse(task, datetime, status);
        }
    }

    public static GeminiTaskResponseBuilder builder() {
        return new GeminiTaskResponseBuilder();
    }
}
