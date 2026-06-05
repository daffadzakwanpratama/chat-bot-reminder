package com.bot.reminder.service;

import com.bot.reminder.dto.GeminiTaskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Mem-parsing teks pesan dari user menggunakan Gemini API
     * dan mengubahnya menjadi bentuk objek GeminiTaskResponse yang terstruktur.
     */
    public GeminiTaskResponse parseTask(String userMessage) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
            log.warn("Gemini API Key belum diatur di file .env! Ekstraksi AI dinonaktifkan. Nilai apiKey yang terbaca: '" + apiKey + "'");
            return GeminiTaskResponse.builder()
                    .status("FAILED")
                    .build();
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            String formattedNow = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String dayOfWeek = now.getDayOfWeek().toString();

            // Memberikan referensi waktu saat ini ke Gemini agar AI dapat menghitung tanggal "besok", "nanti malam", dll.
            String systemInstruction = "Kamu adalah asisten pengambil tugas (task extractor) yang handal. "
                    + "Tugasmu adalah membaca pesan user dan mengekstrak deskripsi tugas utama, detail/catatan tambahan, tanggal/waktu kapan reminder/deadline harus dikirim, kategori tugas, serta pola pengulangan tugas jika ada. "
                    + "Pisahkan antara deskripsi tugas utama dengan detail/instruksi tambahan (masukkan ke field 'notes'). Jika tidak ada detail tambahan, isi field 'notes' dengan string kosong (\"\"). "
                    + "Tentukan 'category' dari salah satu pilihan berikut:\n"
                    + "- KULIAH (untuk kuliah, kelas, praktikum)\n"
                    + "- OLAHRAGA (untuk olahraga, jogging, gym, workout)\n"
                    + "- IBADAH (untuk shalat, ibadah, kebaktian)\n"
                    + "- TUGAS (untuk tugas kuliah/sekolah, deadline PR, project, pengumpulan berkas)\n"
                    + "- BELAJAR (untuk belajar mandiri, membaca buku)\n"
                    + "- UMUM (untuk pengingat umum lainnya)\n"
                    + "Tentukan 'recurrence' (pola pengulangan) dari salah satu pilihan berikut:\n"
                    + "- DAILY (jika diulangi setiap hari)\n"
                    + "- WEEKLY (jika diulangi setiap minggu pada hari tertentu, misal 'setiap senin')\n"
                    + "- NONE (jika tugas hanya terjadi satu kali)\n"
                    + "Gunakan Waktu Sekarang sebagai referensi: " + formattedNow + " (Hari: " + dayOfWeek + "). "
                    + "Jika user berkata 'besok', hitung tanggal besok dari referensi. "
                    + "Jika user berkata 'nanti malam jam 8', hitung tanggal hari ini jam 20:00. "
                    + "Jika user berkata '2 menit lagi', tambahkan 2 menit dari referensi sekarang. "
                    + "Jika itu tugas kuliah/sekolah/PR yang memiliki deadline, set 'category' menjadi 'TUGAS' dan set 'datetime' menjadi waktu deadline mutlaknya. "
                    + "Bersihkan deskripsi tugas utama dari kata kerja bantu/perintah seperti 'ingatkan saya', 'tolong ingatkan', 'jangan lupa untuk', dll. "
                    + "Pastikan waktu reminder/deadline yang kamu berikan bernilai di masa depan dibanding referensi sekarang. "
                    + "Jika tidak ada info tugas atau waktu pengingat yang jelas, set status menjadi 'FAILED'.";

            // Membuat JSON Request Body untuk Gemini API dengan responseSchema
            String requestBody = "{"
                    + "\"contents\": [{"
                    + "  \"parts\": [{"
                    + "    \"text\": \"" + escapeJson(systemInstruction + "\n\nUser Message: " + userMessage) + "\""
                    + "  }]"
                    + "}],"
                    + "\"generationConfig\": {"
                    + "  \"responseMimeType\": \"application/json\","
                    + "  \"responseSchema\": {"
                    + "    \"type\": \"object\","
                    + "    \"properties\": {"
                    + "      \"task\": {"
                    + "        \"type\": \"string\","
                    + "        \"description\": \"Deskripsi tugas utama yang dibersihkan\""
                    + "      },"
                    + "      \"notes\": {"
                    + "        \"type\": \"string\","
                    + "        \"description\": \"Detail, catatan, atau instruksi tambahan tentang tugas. Isi dengan string kosong '' jika tidak ada detail tambahan.\""
                    + "      },"
                    + "      \"datetime\": {"
                    + "        \"type\": \"string\","
                    + "        \"description\": \"Waktu pengingat atau deadline yang dihitung dalam format yyyy-MM-dd HH:mm\""
                    + "      },"
                    + "      \"status\": {"
                    + "        \"type\": \"string\","
                    + "        \"enum\": [\"SUCCESS\", \"FAILED\"],"
                    + "        \"description\": \"SUCCESS jika berhasil mem-parsing tugas dan waktu yang jelas, FAILED jika tidak\""
                    + "      },"
                    + "      \"category\": {"
                    + "        \"type\": \"string\","
                    + "        \"enum\": [\"KULIAH\", \"OLAHRAGA\", \"IBADAH\", \"TUGAS\", \"BELAJAR\", \"UMUM\"],"
                    + "        \"description\": \"Kategori tugas\""
                    + "      },"
                    + "      \"recurrence\": {"
                    + "        \"type\": \"string\","
                    + "        \"enum\": [\"NONE\", \"DAILY\", \"WEEKLY\"],"
                    + "        \"description\": \"Pola pengulangan tugas\""
                    + "      }"
                    + "    },"
                    + "    \"required\": [\"task\", \"notes\", \"datetime\", \"status\", \"category\", \"recurrence\"]"
                    + "  }"
                    + "}"
                    + "}";

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.debug("Mengirim request ke Gemini API...");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini API mengembalikan error. Status: {}, Body: {}", response.statusCode(), response.body());
                return GeminiTaskResponse.builder().status("FAILED").build();
            }

            // Membaca wrapper respon dari Gemini
            Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
            var candidates = (java.util.List<?>) responseMap.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                var candidate = (Map<?, ?>) candidates.get(0);
                var content = (Map<?, ?>) candidate.get("content");
                if (content != null) {
                    var parts = (java.util.List<?>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        var part = (Map<?, ?>) parts.get(0);
                        String jsonText = (String) part.get("text");
                        log.info("JSON hasil parsing yang diekstrak dari Gemini: {}", jsonText);
                        
                        // Mem-parsing string JSON internal ke DTO kita
                        return objectMapper.readValue(jsonText, GeminiTaskResponse.class);
                    }
                }
            }

            return GeminiTaskResponse.builder().status("FAILED").build();
        } catch (Exception e) {
            log.error("Terjadi error saat menghubungi Gemini API", e);
            return GeminiTaskResponse.builder().status("FAILED").build();
        }
    }

    /**
     * Helper untuk mengamankan karakter khusus dalam JSON string.
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
