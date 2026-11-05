# 🗺️ Apex3D Architecture & Codebase Mapping
**Project Path:** `C:\Users\Administrator\Saved Games\ANDROID APP\apex3d-oct-29-2026-8-09-57-am`

Dokumentasi pemetaan struktur modul, paket, dan komponen teknis aplikasi **Apex3D** (Android 3D Modeling & Studio App berbasis OpenGL ES 3.0 dan Jetpack Compose).

---

## 1. Entry Point
- **`app/src/main/java/com/example/MainActivity.kt`**
  - Root Activity Android (`ComponentActivity`).
  - Mengatur `enableEdgeToEdge()` dan meng-host Composable root `StudioScreen()`.

---

## 2. Core 3D Engine (`com.example.studio3d.engine`)
Sistem rendering real-time berbasis **OpenGL ES 3.0**:
- **`Studio3DView.kt`**
  - Viewport kustom turunan dari `GLSurfaceView`.
  - Mengelola touch gesture listener untuk orbit kamera, pan, pinch zoom, serta raycasting drag interaksi pada gizmo 3D.
- **`StudioRenderer.kt`**
  - Mengimplementasikan `GLSurfaceView.Renderer`.
  - Mengontrol render loop OpenGL ES 3.0: render quad background gradien abu-abu gelap, lantai grid tak hingga & sumbu koordinat utama, scene nodes, serta 3D transform gizmo.
- **`Camera.kt`**
  - Orbit & Orthographic camera controller (azimuth, elevasi, jarak, pan, zoom).
  - Menyediakan preset sudut pandang (`+X`, `-X`, `+Y`, `-Y`, `+Z`, `-Z`) serta konversi koordinat 2D layar ke 3D Ray (`screenPointToRay`).
- **`Gizmo.kt`**
  - Logika interaksi transform gizmo 3D (Translate, Rotate, Scale) untuk sumbu X, Y, Z.
  - Perhitungan matematika Ray-Plane intersection dan sistem snapping grid.
- **`Mesh.kt`**
  - Wrapper alokasi dan manajemen buffer VBO/EBO OpenGL ES (vertices, normals, UVs, indices, wireframe) serta siklus upload GL.
- **`MeshFactory.kt`**
  - Generator geometri prosedural: Cube, Cylinder, Sphere, Wedge, Car Body, Wheels, Ground Grid, Main Axes, Gizmo Handles, dan Fullscreen Quad.
- **`Shaders.kt`**
  - Kompilasi dan manajemen program shader GLSL ES 3.0:
    - `FRAGMENT_SHADER_UNLIT`: Wireframe & Gizmo handle.
    - `FRAGMENT_SHADER_CLAY`: Solid studio clay form dengan pencahayaan warm key + cool fill.
    - `FRAGMENT_SHADER_MATERIAL`: PBR Blinn-Phong material preview (diffuse, specular, metallic flakes, clearcoat, selection highlight).
    - `FRAGMENT_SHADER_STUDIO`: Showroom studio render lighting dengan ambient occlusion floor contact.
    - `FRAGMENT_SHADER_GRID`: Procedural Screen-Space Anti-Aliased Blender Grid Shader menggunakan `fwidth()` dan analitik `smoothstep` (anti-aliased 1.5px–2.5px screen width, 0% flickering, bebas limitasi line width mobile GPU, dilengkapi horizon distance fade).
    - `FRAGMENT_SHADER_BG`: Gradien kanvas studio abu-abu gelap (#242629 - #383B40).

---

## 3. Math 3D Library (`com.example.studio3d.math`)
Modul matematika 3D performa tinggi tanpa library eksternal berat:
- **`Vector3.kt`**: Operasi vektor 3D (dot product, cross product, normalize, distance, lerp, skala).
- **`Matrix4.kt`**: Operasi matriks 4x4 (TRS transformasi, LookAt, Perspective, Orthographic, Inversi, Multiplikasi).
- **`Ray.kt`**: Raycasting untuk picking objek dan deteksi interaksi gizmo (Ray-Plane, Ray-AABB, Ray-Sphere).
- **`BoundingBox.kt`**: Axis-Aligned Bounding Box (AABB) untuk hit-testing dan perhitungan batas objek.

---

## 4. Scene Management & Data (`com.example.studio3d.scene`)
- **`SceneGraph.kt`**
  - Struktur pohon hirarki objek/node (relasi parent-child, query node, penanda ID objek yang sedang dipilih/selected).
- **`SceneNode.kt`**
  - Entitas dalam scene 3D (posisi, rotasi, skala, tipe geometri mesh, material, status visibilitas, status lock).
- **`Material.kt`**
  - Definisi properti permukaan visual (RGBA, metallic, roughness, clearcoat, wireframe flag).
- **`StarterScene.kt`**
  - Generator scene awal:
    - `populateDefaultScene()`: Menginisialisasi scene awal dengan objek default kubus 3D di tengah lantai grid.
    - `populateAutomotiveScene()`: Template konsep bodi mobil sport blockout.

---

## 5. History / Undo-Redo (`com.example.studio3d.history`)
Sistem riwayat aksi interaktif pengguna berbasis Command Pattern:
- **`Command.kt`**
  - Interface command dan concrete class: `AddNodeCommand`, `DeleteNodeCommand`, `TransformNodeCommand`, `ChangeMaterialCommand`, `GroupNodesCommand`.
- **`CommandHistory.kt`**
  - Stack undo dan redo dengan StateFlow observabel `canUndo` dan `canRedo`.

---

## 6. Persistensi & Proyek (`com.example.studio3d.project`)
- **`ProjectManager.kt`**
  - Serialisasi dan deserialisasi struktur scene 3D ke format JSON (`.studio3d`).
  - Mekanisme autosave latar belakang berkala (setiap 30 detik) dan recovery autosave session.
  - Pengelolaan direktori penyimpanan lokal proyek.

---

## 7. Antarmuka Pengguna Jetpack Compose (`com.example.studio3d.ui`)
Lapisan UI modern berbasis Jetpack Compose:
- **`StudioViewModel.kt`**
  - State manager pusat (MVVM pattern) yang menghubungkan state Compose dengan engine 3D dan SceneGraph (`shadingMode`, `transformMode`, `isSnapping`, `selectedNode`, `activePanel`, `projectName`).
- **`StudioScreen.kt`**
  - Layar utama viewport 3D dengan overlay kontrol:
    - **Kiri Atas**: Menu dropdown Shading Mode / Material Preview (latar transparan).
    - **Kanan Atas**: Tombol perspektif kamera X, Y, Z (latar transparan).
    - **Kiri Tengah**: FAB menu radial/pie menu kontekstual.
    - **Bawah**: Floating action bar (Undo, Redo, Add Primitive, Outliner, Inspector, Project Manager).
- **`InspectorSheet.kt`**
  - Bottom sheet untuk inspeksi dan edit numerik transform (Posisi X/Y/Z, Rotasi, Skala) serta material editor (warna RGBA, slider metallic, roughness, clearcoat).
- **`OutlinerSheet.kt`**
  - Bottom sheet tree view daftar seluruh objek dalam scene (seleksi, visibilitas toggle, duplikasi, hapus, grouping).
- **`AddMeshSheet.kt`**
  - Bottom sheet katalog primitive 3D untuk menambahkan objek baru ke scene.
- **`ProjectDialog.kt`**
  - Modal dialog untuk manajemen Save Project, Open Project, dan New Project.
- **`RadialToolMenu.kt`**
  - Menu melingkar (pie menu) cepat untuk memilih mode Transform (Translate, Rotate, Scale) dan Shading Mode.
- **`ToolSelectorPanel.kt` & `TransformSelectorPanel.kt`**
  - Panel bantuan pemilihan tool transformasi alternatif.

---

## 8. Desain & Tema (`com.example.ui.theme`)
- **`Color.kt`**: Token warna studio (background, surface, aksen cyan, aksen amber, sumbu merah/hijau/biru).
- **`Theme.kt`**: Setup Dark Theme Material3 `Apex3DTheme`.
- **`Type.kt`**: Definisi tipografi teks aplikasi.
