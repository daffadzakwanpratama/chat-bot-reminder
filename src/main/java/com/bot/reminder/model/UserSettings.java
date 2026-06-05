package com.bot.reminder.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "city_id", nullable = false)
    private String cityId;

    @Column(name = "city_name", nullable = false)
    private String cityName;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    public UserSettings() {
    }

    public UserSettings(Long chatId, String cityId, String cityName, LocalDateTime lastUpdated) {
        this.chatId = chatId;
        this.cityId = cityId;
        this.cityName = cityName;
        this.lastUpdated = lastUpdated;
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getCityId() {
        return cityId;
    }

    public void setCityId(String cityId) {
        this.cityId = cityId;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
