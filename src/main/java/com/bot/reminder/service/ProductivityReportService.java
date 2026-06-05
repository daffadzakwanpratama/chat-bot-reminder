package com.bot.reminder.service;

import com.bot.reminder.model.Task;
import com.bot.reminder.model.UserSettings;
import com.bot.reminder.repository.TaskRepository;
import com.bot.reminder.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductivityReportService {

    private final TaskRepository taskRepository;
    private final UserSettingsRepository userSettingsRepository;

    public ProductivityReportService(TaskRepository taskRepository, UserSettingsRepository userSettingsRepository) {
        this.taskRepository = taskRepository;
        this.userSettingsRepository = userSettingsRepository;
    }

    /**
     * Menyusun pesan laporan produktivitas mingguan untuk user tertentu.
     */
    public String generateWeeklyReport(Long chatId) {
        // Ambil zona waktu user
        String tz = userSettingsRepository.findById(chatId)
                .map(UserSettings::getTimezone)
                .orElse("Asia/Jakarta");
        ZoneId userZone = ZoneId.of(tz);

        // Ambil waktu 7 hari lalu di zona waktu user
        ZonedDateTime userNow = ZonedDateTime.now(userZone);
        ZonedDateTime userStart = userNow.minusDays(7);

        // Konversi ke waktu server untuk query database
        LocalDateTime serverStart = userStart.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

        // Ambil tugas dari database
        List<Task> allTasks = taskRepository.findByChatIdAndCreatedAtAfter(chatId, serverStart);

        // Filter out kategori IBADAH (karena jadwal shalat otomatis)
        List<Task> filteredTasks = allTasks.stream()
                .filter(t -> !"IBADAH".equalsIgnoreCase(t.getCategory()))
                .collect(Collectors.toList());

        long totalTasks = filteredTasks.size();
        long completedTasks = filteredTasks.stream().filter(Task::isCompleted).count();
        long completionPercentage = totalTasks == 0 ? 0 : Math.round((completedTasks * 100.0) / totalTasks);

        // Hitung rincian per kategori
        Map<String, Long> categoryStats = new HashMap<>();
        String[] categories = {"KULIAH", "OLAHRAGA", "TUGAS", "BELAJAR", "UMUM"};
        for (String cat : categories) {
            categoryStats.put(cat, 0L);
        }

        for (Task task : filteredTasks) {
            if (task.isCompleted()) {
                String cat = task.getCategory() != null ? task.getCategory().toUpperCase() : "UMUM";
                categoryStats.put(cat, categoryStats.getOrDefault(cat, 0L) + 1);
            }
        }

        // Teks rentang tanggal di zona waktu user
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy");
        String startText = userStart.format(dateFormatter);
        String endText = userNow.format(dateFormatter);

        StringBuilder report = new StringBuilder();
        report.append("📊 <b>LAPORAN PRODUKTIVITAS MINGGUAN Anda</b>\n");
        report.append("==================================\n");
        report.append("📅 Periode: <b>").append(startText).append("</b> - <b>").append(endText).append("</b>\n\n");

        report.append("📈 <b>Statistik Utama:</b>\n");
        report.append("• Total Tugas Dibuat: <b>").append(totalTasks).append("</b>\n");
        report.append("• Tugas Selesai: <b>").append(completedTasks).append("</b>\n");
        report.append("• Skor Produktivitas: <b>").append(completionPercentage).append("%</b>\n\n");

        report.append("🗂️ <b>Rincian Tugas Selesai per Kategori:</b>\n");
        report.append("🎓 Kuliah: <b>").append(categoryStats.get("KULIAH")).append("</b> selesai\n");
        report.append("🏃 Olahraga: <b>").append(categoryStats.get("OLAHRAGA")).append("</b> selesai\n");
        report.append("📝 Tugas / PR: <b>").append(categoryStats.get("TUGAS")).append("</b> selesai\n");
        report.append("📚 Belajar: <b>").append(categoryStats.get("BELAJAR")).append("</b> selesai\n");
        report.append("🔔 Pengingat Umum: <b>").append(categoryStats.get("UMUM")).append("</b> selesai\n\n");

        report.append("💡 <b>Kata Motivasi Minggu Ini:</b>\n");
        report.append("<i>\"").append(getMotivationalQuote(completionPercentage, totalTasks)).append("\"</i>");

        return report.toString();
    }

    private String getMotivationalQuote(long percentage, long totalTasks) {
        if (totalTasks == 0) {
            return "Tenang dan santai! Tidak ada tugas yang terjadwal minggu lalu. Siap untuk memulai minggu baru dengan rencana yang segar? 🚀";
        }
        if (percentage >= 80) {
            return "Luar biasa! Produktivitas Anda sangat tinggi minggu ini. Pertahankan konsistensi luar biasa ini untuk mencapai target-target hebat Anda! 🔥🏆";
        } else if (percentage >= 50) {
            return "Kerja bagus! Anda telah menyelesaikan sebagian besar tugas Anda. Yuk lebih fokus lagi minggu depan agar hasilnya semakin optimal! 💪📈";
        } else {
            return "Tetap semangat! Rutinitas terkadang memang berat. Ingat, satu langkah kecil hari ini jauh lebih baik dibanding tidak melangkah sama sekali. Mari kita mulai cicil kembali esok hari! 🌟✍️";
        }
    }
}
