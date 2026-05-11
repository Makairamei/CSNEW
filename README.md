# BetbetMiro Extension Repository

Repository ini berisi kumpulan extension provider untuk Cloudstream (CS3). Semua extension dibuat modular supaya mudah dikembangkan, diperbaiki, dan tetap stabil saat ada update per provider.

---

## 📦 Fitur

- Multi extension (tiap provider berdiri sendiri)
- Update terpisah tanpa ganggu extension lain
- Build otomatis via GitHub Actions
- Output format `.cs3` kompatibel Cloudstream
- Struktur ringan, mudah dikembangkan dan di-debug

---

## 🚀 Cara Install di Cloudstream

1. Buka aplikasi Cloudstream
2. Masuk ke Settings
3. Pilih Extensions / Repository
4. Tambahkan URL berikut:

https://raw.githubusercontent.com/sad25kag/BetbetMiro-Extension/builds/plugins.json

5. Restart aplikasi
6. Extension akan otomatis muncul

---

## ⚙️ Build Manual (Opsional)

Jalankan perintah berikut di root project:

./gradlew make makePluginsJson
./gradlew ensureJarCompatibility

Hasil build akan muncul di:

build/*.cs3  
build/plugins.json  

---

## 🔄 CI/CD Workflow

GitHub Actions akan otomatis:

- Build semua provider
- Generate plugins.json
- Menyimpan hasil ke branch builds

Jika salah satu module error:
➡️ build tetap lanjut (tidak menghentikan keseluruhan proses)

---

## 🧠 Konsep Project

- Setiap provider independen
- Tidak saling bergantung
- Update lebih aman & terkontrol
- Fokus ke modular system

---

## ⚠️ Disclaimer

Project ini dibuat untuk tujuan edukasi dan pengembangan plugin Cloudstream.  
Segala penggunaan berada di tanggung jawab pengguna.

---

## 🙏 Special Thanks / Credits

- Cloudstream Project (core framework)
- Semua contributor & tester
- Open-source community
- Dev yang terus support ekosistem extension

---

## 💡 Catatan

Kalau ada error:
Jangan rebuild semuanya — cukup cek module yang bermasalah saja.
