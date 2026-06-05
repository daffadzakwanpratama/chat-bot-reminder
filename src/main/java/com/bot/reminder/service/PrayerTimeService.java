package com.bot.reminder.service;

import com.bot.reminder.model.Reminder;
import com.bot.reminder.model.Task;
import com.bot.reminder.repository.ReminderRepository;
import com.bot.reminder.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrayerTimeService {

    private static final Logger log = LoggerFactory.getLogger(PrayerTimeService.class);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskRepository taskRepository;
    private final ReminderRepository reminderRepository;

    public PrayerTimeService(TaskRepository taskRepository, ReminderRepository reminderRepository) {
        this.taskRepository = taskRepository;
        this.reminderRepository = reminderRepository;
    }

    /**
     * Mencari ID Kota berdasarkan nama kota
     */
    public List<Map<String, String>> searchCity(String cityName) {
        List<Map<String, String>> resultList = new ArrayList<>();
        try {
            String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
            String url = "https://api.myquran.com/v2/sholat/kota/cari/" + encodedCity;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
                Boolean status = (Boolean) responseMap.get("status");
                if (status != null && status) {
                    List<?> data = (List<?>) responseMap.get("data");
                    if (data != null) {
                        for (Object obj : data) {
                            if (obj instanceof Map) {
                                Map<?, ?> map = (Map<?, ?>) obj;
                                Map<String, String> cityInfo = new HashMap<>();
                                cityInfo.put("id", String.valueOf(map.get("id")));
                                cityInfo.put("lokasi", String.valueOf(map.get("lokasi")));
                                resultList.add(cityInfo);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Gagal melakukan pencarian kota: {}", cityName, e);
        }
        return resultList;
    }

    /**
     * Mengambil jadwal shalat suatu kota pada tanggal tertentu
     */
    public Map<String, String> getPrayerTimes(String cityId, LocalDate date) {
        Map<String, String> prayerTimes = new HashMap<>();
        try {
            String url = String.format("https://api.myquran.com/v2/sholat/jadwal/%s/%d/%02d/%02d",
                    cityId, date.getYear(), date.getMonthValue(), date.getDayOfMonth());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
                Boolean status = (Boolean) responseMap.get("status");
                if (status != null && status) {
                    Map<?, ?> data = (Map<?, ?>) responseMap.get("data");
                    if (data != null) {
                        Map<?, ?> jadwal = (Map<?, ?>) data.get("jadwal");
                        if (jadwal != null) {
                            String[] prayers = {"subuh", "dzuhur", "ashar", "maghrib", "isya"};
                            for (String prayer : prayers) {
                                if (jadwal.containsKey(prayer)) {
                                    prayerTimes.put(prayer, String.valueOf(jadwal.get(prayer)));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Gagal mengambil jadwal shalat untuk kota ID: {}, Tanggal: {}", cityId, date, e);
        }
        return prayerTimes;
    }

    /**
     * Mengambil nama kota berdasarkan ID kota dengan memanggil jadwal hari ini
     */
    public String getCityNameById(String cityId) {
        try {
            LocalDate date = LocalDate.now();
            String url = String.format("https://api.myquran.com/v2/sholat/jadwal/%s/%d/%02d/%02d",
                    cityId, date.getYear(), date.getMonthValue(), date.getDayOfMonth());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
                Boolean status = (Boolean) responseMap.get("status");
                if (status != null && status) {
                    Map<?, ?> data = (Map<?, ?>) responseMap.get("data");
                    if (data != null) {
                        return String.valueOf(data.get("lokasi"));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Gagal mengambil nama kota untuk ID: {}", cityId, e);
        }
        return null;
    }

    /**
     * Menjadwalkan pengingat shalat otomatis untuk user tertentu pada hari tertentu
     */
    public boolean scheduleDailyPrayersForUser(Long chatId, String cityId, String cityName, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // Jika sudah terjadwal untuk hari ini, lewati
        boolean exists = reminderRepository.existsByTask_ChatIdAndTask_CategoryAndReminderTimeBetween(
                chatId, "IBADAH", startOfDay, endOfDay);
        if (exists) {
            log.debug("Jadwal shalat hari ini sudah tersimpan untuk chatId: {}", chatId);
            return false;
        }

        Map<String, String> times = getPrayerTimes(cityId, date);
        if (times.isEmpty()) {
            log.warn("Jadwal shalat kosong untuk kota ID: {} pada tanggal: {}", cityId, date);
            return false;
        }

        String[] prayerNames = {"Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"};
        int scheduledCount = 0;

        for (String prayerName : prayerNames) {
            String timeStr = times.get(prayerName.toLowerCase());
            if (timeStr == null || timeStr.isEmpty()) {
                continue;
            }

            try {
                // Parsing timeStr "HH:mm"
                String[] parts = timeStr.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                LocalDateTime reminderTime = LocalDateTime.of(date, LocalTime.of(hour, minute));

                // Hanya menjadwalkan yang ada di masa depan
                if (reminderTime.isAfter(LocalDateTime.now())) {
                    Task task = new Task();
                    task.setChatId(chatId);
                    task.setDescription("Shalat " + prayerName);
                    task.setCategory("IBADAH");
                    task.setRecurrence("NONE");
                    task.setNotes("Jadwal shalat otomatis di " + cityName);
                    task.setNotified(false);
                    task.setReminderTime(reminderTime);
                    
                    // Membuat pesan dengan format HTML yang cantik
                    String formattedMsg = String.format("🕌 <b>WAKTUNYA SHALAT %s!</b>\n\nUntuk wilayah <b>%s</b> dan sekitarnya.\nWaktu Adzan: <b>%s WIB</b>.\n\n<i>\"Mari menunaikan ibadah shalat tepat waktu. Sesungguhnya shalat memiliki keutamaan yang besar.\"</i>",
                            prayerName.toUpperCase(), cityName, timeStr);

                    Reminder reminder = new Reminder(task, reminderTime, "PRAYER", formattedMsg);
                    task.addReminder(reminder);

                    taskRepository.save(task);
                    scheduledCount++;
                }
            } catch (Exception e) {
                log.error("Gagal menjadwalkan shalat {} untuk chatId: {}", prayerName, chatId, e);
            }
        }

        log.info("Berhasil menjadwalkan {} pengingat shalat untuk chatId: {} di {}", scheduledCount, chatId, cityName);
        return scheduledCount > 0;
    }
}
