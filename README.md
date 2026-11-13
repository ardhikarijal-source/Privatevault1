# Private Vault

Aplikasi Android brankas pribadi: PIN + biometrik, folder, foto/video/CBZ terenkripsi (AES-256).

## Build APK lewat GitHub (tanpa Android Studio)

1. Buat akun di https://github.com lalu klik **New repository** (nama bebas, mis. `private-vault`, boleh Private).
2. Upload isi folder proyek ini ke repository:
   - **Lewat web:** buka repository → **Add file → Upload files** → seret *seluruh isi* folder `PrivateVault`
     (pastikan folder `.github` ikut ter-upload) → **Commit changes**.
   - **Atau lewat terminal:**
     ```bash
     cd PrivateVault
     git init && git add . && git commit -m "first commit"
     git branch -M main
     git remote add origin https://github.com/USERNAME/private-vault.git
     git push -u origin main
     ```
3. Buka tab **Actions** → workflow **Build APK** akan jalan otomatis (±5–8 menit).
   Jika tidak jalan, pilih **Build APK → Run workflow**.
4. Setelah centang hijau, klik run tersebut → bagian **Artifacts** → unduh **PrivateVault-debug-apk** (zip berisi `app-debug.apk`).
5. Ekstrak, kirim `app-debug.apk` ke HP, lalu buka dan izinkan **Install dari sumber tidak dikenal**.

Jika build gagal (tanda ✗), buka run-nya, salin pesan error merah pada langkah **Build debug APK**, dan kirim ke Claude untuk diperbaiki.

## Catatan
- APK ini bertanda tangan debug (cukup untuk dipakai pribadi, bukan untuk Play Store).
- Jika PIN terlupa, hapus data aplikasi (semua file di brankas ikut hilang).