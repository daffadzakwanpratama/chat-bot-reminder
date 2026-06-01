# 🤖 AI-Powered Telegram Task Reminder Bot

Chatbot Telegram interaktif yang berfungsi sebagai asisten pengingat tugas (task reminder) pribadi. Bot ini memanfaatkan kecerdasan buatan **Gemini 3.5 Flash** untuk mengekstrak tugas dan waktu pengingat secara otomatis dari kalimat bahasa alami (natural language) sehari-hari, serta menyimpannya ke database relasional lokal **SQLite**.

Aplikasi ini dibangun menggunakan **Java 21/24** dan **Spring Boot 3.x**, menampilkan penjadwalan otomatis (scheduling) di latar belakang untuk mengirimkan notifikasi pengingat tepat waktu ke chatroom Telegram pengguna.

---

## 🌟 Fitur Utama (MVP Version 1)

1. **AI Natural Language Processing (Gemini API)**:
   * Pengguna tidak perlu menghafal format perintah yang kaku. Cukup ketik teks biasa seperti:
     * *"ingatkan saya belajar Java besok jam 7 malam"*
     * *"tolong buat kopi 2 menit lagi"*
   * Gemini 3.5 Flash secara otomatis mem-parsing deskripsi tugas dan menghitung tanggal & waktu target pengingat secara presisi, lalu mengembalikannya sebagai data terstruktur (JSON Schema).
2. **Persistent Storage (SQLite & Spring Data JPA)**:
   * Tugas disimpan secara permanen di database berbasis file SQLite (`reminder.db`).
   * Data tidak akan hilang sekalipun server/aplikasi dimatikan.
3. **Background Scheduler (Spring Scheduler)**:
   * Background worker berjalan otomatis setiap 30 detik untuk memeriksa tugas yang jatuh tempo.
   * Mengirimkan notifikasi pengingat otomatis ("🔔 **PENGINGAT TUGAS!**") secara push ke Telegram user.
4. **Interactive Bot Commands**:
   * `/start` : Panduan penggunaan awal dan perkenalan bot.
   * `/daftar` : Menampilkan list seluruh tugas aktif pengguna yang terdaftar di database.
   * `/hapus [nomor]` : Menghapus/menyelesaikan tugas dari daftar secara manual.

---

## 🛠️ Tech Stack

* **Language**: Java 21 / 24
* **Backend Framework**: Spring Boot 3.2.5
* **Database**: SQLite JDBC (dengan Hibernate Community Dialect)
* **ORM**: Spring Data JPA
* **APIs**:
  * Telegram Bot API (`telegrambots-spring-boot-starter`)
  * Google Gemini AI API (`v1beta/gemini-3.5-flash`)
* **Environment Manager**: Dotenv Java

---

## 🚀 Cara Menjalankan Proyek Secara Lokal

### 1. Prasyarat
Pastikan komputer Anda sudah terpasang:
* **Java 21** atau versi lebih baru
* **Maven** (jika tidak ada, gunakan pembungkus lokal/wrapper)

### 2. Kloning Repositori
```bash
git clone https://github.com/username/chat-bot-reminder.git
cd chat-bot-reminder
```

### 3. Konfigurasi Kredensial (.env)
Salin file `.env.example` menjadi `.env` di folder root proyek Anda:
```bash
cp .env.example .env
```
Buka file `.env` baru tersebut, lalu isi dengan kredensial Anda:
```env
TELEGRAM_BOT_TOKEN=YOUR_TELEGRAM_BOT_TOKEN_HERE
TELEGRAM_BOT_USERNAME=YOUR_TELEGRAM_BOT_USERNAME_HERE
GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
```
> ⚠️ **PENTING**: File `.env` berisi kunci rahasia Anda dan sudah otomatis dikecualikan oleh `.gitignore`. Jangan pernah mengunggah file `.env` asli ke repositori publik!

### 4. Jalankan Aplikasi
Gunakan perintah Maven berikut di terminal Anda:
```bash
mvn spring-boot:run
```
*(Atau jika menggunakan Maven lokal yang terbungkus, gunakan `.\.maven\apache-maven-3.9.6\bin\mvn spring-boot:run`)*.

---

## 📊 Skema Database (SQLite)
Aplikasi secara otomatis mendeteksi dan membuat tabel **`tasks`** di SQLite saat dijalankan pertama kali:

| Kolom | Tipe Data | Deskripsi |
| :--- | :--- | :--- |
| `id` | INTEGER (PK) | ID Unik Tugas (Auto Increment) |
| `chat_id` | BIGINT | ID Chatroom Telegram Pengguna |
| `description` | VARCHAR | Isi Deskripsi Tugas |
| `reminder_time` | TIMESTAMP | Tanggal & Waktu Notifikasi Dikirim |
| `is_notified` | BOOLEAN | Status Pengiriman Notifikasi |
| `created_at` | TIMESTAMP | Tanggal Pembuatan Tugas |
