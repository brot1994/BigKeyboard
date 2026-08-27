# Big Keyboard — Custom Android Keyboard

Custom keyboard (IME) Android dengan layout tombol besar 5-kolom, sesuai desain
di gambar referensi (QWERTY yang disusun ulang jadi kotak-kotak besar).

## Cara Build APK — Tanpa Android Studio (GitHub Actions)

1. Buat repository baru di GitHub (bisa private).
2. Upload semua isi folder ini ke repo tersebut (drag & drop di web GitHub juga bisa,
   atau `git push`).
3. Buka tab **Actions** di repo, workflow "Build APK" akan otomatis jalan
   setiap ada push ke branch `main`/`master` (atau klik "Run workflow" manual).
4. Setelah selesai (±2-3 menit), buka hasil run tsb → bagian **Artifacts** →
   download `BigKeyboard-debug-apk.zip`. Di dalamnya ada `app-debug.apk`.
5. Pindahkan APK ke HP Android, install (aktifkan "Install dari sumber tidak dikenal"
   kalau diminta).

## Cara Build APK — Dengan Android Studio

1. Install Android Studio (gratis): https://developer.android.com/studio
2. Buka folder project ini lewat **File → Open**.
3. Tunggu Gradle sync selesai.
4. Klik menu **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
5. APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Cara Mengaktifkan Keyboard di HP

1. Install APK-nya.
2. Buka app "Big Keyboard" → tekan tombol **"1. Aktifkan Keyboard"**
   → aktifkan toggle "Big Keyboard" di daftar keyboard.
3. Kembali ke app, tekan **"2. Pilih Keyboard Ini"** → pilih "Big Keyboard".
4. Buka aplikasi chat/apapun yang ada kolom teks → keyboard custom akan muncul.

## Struktur Layout Keyboard (sesuai gambar)

```
Q  E  T  U  O
W  R  Y  I  P
A  D  G  J  L
S  F  H  K  ,
Z  C  B  M  .
X  V  N  ?  [DEL]
[Shift] [?123] [Space] [Switch] [Enter]
```

Tombol **?123** pindah ke halaman angka & simbol (1-0, @#$%&, dll),
tombol **ABC** di halaman itu kembali ke huruf.

## Perilaku Khusus

- **DEL**: sekali pencet = hapus 1 huruf. Tahan ±3 detik = hapus semua teks di kolom.
- **Kapital otomatis**: huruf pertama saat mulai ketik, dan huruf pertama setelah
  tanda titik (`.`), otomatis besar. Huruf berikutnya kembali kecil.
- **Shift (⇧)**: sekali pencet = kapital untuk 1 huruf berikutnya saja.
  Pencet 2x cepat (double-tap) = kunci semua huruf jadi besar (caps lock).
  Untuk melepas kuncian, pencet ⇧ sekali.

## Yang Bisa Dikustomisasi

- Warna tombol: edit `app/src/main/res/drawable/key_background.xml`
- Susunan huruf/tombol: edit `app/src/main/res/xml/keyboard_layout.xml`
- Ukuran tombol: ubah `android:keyWidth` / `android:keyHeight` di file XML keyboard
- Nama app & label: `app/src/main/res/values/strings.xml`
