# Apex3D — Mobile 3D Modeling & Mesh Editing Studio for Android

<div align="center">
  <h3>Eksperimental Mobile 3D Mesh Editor & Sculpting Engine berbasis Kotlin & OpenGL ES 3.0</h3>
</div>

---

## 📌 Ringkasan Proyek (Project Overview)

**Apex3D** adalah proyek aplikasi Android eksperimental yang dirancang untuk membawa kapabilitas pemodelan dan pengeditan mesh 3D langsung ke perangkat mobile layar sentuh (*touchscreen*). 

Proyek ini dibangun secara *native* menggunakan **Kotlin**, **Jetpack Compose** untuk antarmuka pengguna (UI), dan **OpenGL ES 3.0** untuk *rendering pipeline* serta manipulasi geometri tingkat rendah (*low-level mesh geometry*).

> **Status Proyek**: *Archived / Open for Community Continuation*.  
> Proyek ini diunggah untuk umum agar pengembang, komunitas grafika komputer 3D, atau siapa saja yang tertarik mengembangkan aplikasi 3D di Android dapat melanjutkan, mempelajari, atau memanfaatkannya sebagai referensi arsitektur.

---

## ✨ Fitur yang Sudah Terimplementasi

### 1. 3D Engine & Viewport
- **OpenGL ES 3.0 Rendering Engine**: Shading interaktif, normal lighting, wireframe mode, dan grid lantai 3D.
- **Orbit & Pan Camera**: Navigasi kamera orbit sentuh halus (*touch gestures* dengan sensitivitas yang dapat dikonfigurasi).
- **Interactive Transform Gizmo**: Manipulator translasi, rotasi, dan skala langsung di viewport 3D.

### 2. Format File 3D (Import / Export)
- **Wavefront OBJ**: Parser `.obj` native dengan pembacaan vertex, normal, UV, dan material dasar.
- **glTF / GLB**: Parser glTF binary (`.glb`) dengan dukungan `MeshoptDecoder` untuk dekompresi mesh.

### 3. Operasi Pemodelan Mesh (Mesh Editing Tools)
- **Extrude**: Penarikan face/polygon secara interaktif dengan kontrol slider offset.
- **Bevel (Chamfer)**: Algoritma bevel edge dan vertex dengan multi-segment curve profile.
- **Inset**: Inset faces untuk membuat topologi baru ke dalam permukaan poligon.
- **Loop Cut**: Pemotongan loop edge untuk menambah resolusi geometri.
- **Bridge**: Menghubungkan edge loops yang terpisah.
- **Subdivide**: Pembagian poligon untuk menambah kerapatan vertex.
- **Mesh Primitives Generator**: Kubus, silinder, bola (UV Sphere/IcoSphere), torus, dan plane.

### 4. Manajemen Riwayat & Topologi (Undo / Redo)
- **Command History Pattern**: Setiap perubahan topologi mesh dan transformasi tercatat dalam riwayat *undo / redo* aman memori.

### 5. Antarmuka (Modern Jetpack Compose)
- Panel inspektor properti transformasi (*Transform Inspector Card*).
- Popup kontrol slider interaktif (*Real-time parameter adjustment*).
- Desain responsif ramah sentuhan untuk smartphone dan tablet Android.

---

## 🛠️ Tech Stack & Arsitektur

- **Bahasa**: Kotlin (100% Native)
- **Rendering**: OpenGL ES 3.0 (GLSurfaceView / Custom Surface)
- **UI Framework**: Android Jetpack Compose + Material 3
- **Build System**: Gradle KTS (Kotlin DSL)
- **Target SDK**: Android 16 (API 36) | **Min SDK**: Android 7.0 (API 24)

---

## 🚀 Cara Menjalankan Proyek Secara Lokal

### Prasyarat
1. **Android Studio** (Koala / Ladybug atau versi terbaru)
2. **JDK 17 / JDK 21**
3. Perangkat fisik Android dengan dukungan minimal OpenGL ES 3.0 atau Android Emulator.

### Langkah Build & Run

1. **Clone repository ini**:
   ```bash
   git clone https://github.com/USERNAME/REPO-NAME.git
   cd REPO-NAME
   ```

2. **Buka di Android Studio**:
   - Pilih **File > Open**, lalu arahkan ke folder proyek.
   - Biarkan Gradle Sync berjalan hingga selesai.

3. **Atau Build via Command Line (Terminal)**:
   ```bash
   # Build APK Debug
   ./gradlew assembleDebug

   # Install & Run ke perangkat yang terhubung USB
   adb install -r -t app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.aistudio.apex3d.kxzmw/com.example.MainActivity
   ```

---

## 🤝 Ingin Melanjutkan Proyek Ini? (Contributing)

Proyek ini sangat terbuka untuk siapa saja yang ingin melanjutkan pengembangannya! Beberapa ide roadmap yang bisa dieksplorasi:
- [ ] Export mesh ke format `.obj` / `.glb` yang telah dimodifikasi.
- [ ] Sculpting brushes (Grab, Smooth, Inflate, Flatten).
- [ ] Texture painting & UV mapping editor.
- [ ] PBR (Physically Based Rendering) material preview.

Jika Anda membuat cabang baru (*fork*) atau melanjutkan aplikasi ini, silakan buat *Pull Request* atau cantumkan kredit ke repository ini.

---

## 📄 Lisensi
Didistribusikan di bawah lisensi terbuka (Open Source). Silakan pelajari dan gunakan untuk kepentingan belajar maupun pengembangan lebih lanjut.
