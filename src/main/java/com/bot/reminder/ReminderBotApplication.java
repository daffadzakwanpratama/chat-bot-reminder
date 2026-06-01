package com.bot.reminder;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReminderBotApplication {

    public static void main(String[] args) {
        // Memuat file .env jika ada
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        
        // Memasukkan setiap variabel lingkungan ke System Properties agar Spring Boot dapat membacanya
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });

        SpringApplication.run(ReminderBotApplication.class, args);
    }
}
