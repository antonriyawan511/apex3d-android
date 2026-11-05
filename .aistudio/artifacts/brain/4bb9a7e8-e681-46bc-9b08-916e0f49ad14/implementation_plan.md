# Apex3D — Automotive & Hard-Surface 3D Studio (Phase 1)

Apex3D is a high-performance native Android hard-surface and automotive 3D design studio. Phase 1 establishes the rock-solid graphics foundation: an OpenGL ES 3.0 real-time viewport, dual-thumb camera orbit/pan/zoom gestures, interactive 3D transform gizmos (Translate, Rotate, Scale) with snapping, a full scene graph outliner, object inspector, command-based Undo/Redo history, and native project persistence (.studio3d) with autosave recovery.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following technical and UX choices were confirmed for Phase 1 based on design requirements:

- **Confirmed Decision 1 (Rendering Backend)**: Native OpenGL ES 3.0 via Android `GLSurfaceView` with custom GLSL shaders (vertex/fragment pipelines for Wireframe, Clay/Solid, Material Preview, and Studio Render Preview). Zero heavy external C++ runtimes or fragile dependencies, ensuring 60+ FPS rendering and instant compilation.
- **Confirmed Decision 2 (Starter Scene)**: Loads with a ready-to-inspect automotive blockout (aerodynamic sports chassis, aerodynamic cabin, wheels, axles, and headlights) on a studio ground grid.
- **Confirmed Decision 3 (Brand & Aesthetic)**: *Apex3D Automotive & Hard-Surface Studio* — dark luxury industrial styling (#0D0F14 charcoal foundation, cyan/amber telemetry accents, floating frosted glass HUD panels, and non-blocking two-thumb ergonomics).

---

## 1. Overview & Core Concept

- **What It Does**: Provides industrial designers, automotive enthusiasts, and 3D artists with a pocket hard-surface CAD/modeling studio on Android. Users can construct, transform, duplicate, inspect, and group 3D geometry in a viewport with studio lighting, automotive car paint materials, and precise snapping.
- **Target Audience**: 3D modelers, vehicle concept artists, industrial design students, and hard-surface creators needing precision modeling workflows on phone or tablet.
- **Key Value**: Desktop-grade 3D manipulation capabilities (X/Y/Z gizmos, outliner hierarchy, undo/redo, camera presets) adapted for mobile touch ergonomics with zero input collisions.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **First Launch & Starter Scene**:
   - The user opens Apex3D directly into a fullscreen 3D studio viewport featuring a sleek ground grid and an automotive blockout assembly.
   - Floating HUD reveals primary navigation (Camera Preset pill, Shading Mode toggle), the Transform Toolbar (Select, Move, Rotate, Scale, Snap), and bottom bar for Outliner, Add Primitive, Undo/Redo, and Project File menu.
2. **Object Creation & Transformation**:
   - Tapping **+ Add** presents a bottom sheet with Primitives (Cube, Cylinder, Sphere, Wedge, Car Chassis blockout, Wheel, Floor Plane).
   - Selecting any object in the viewport or outliner highlights it with an active accent outline and projects a color-coded 3D Gizmo (Red = X, Green = Y, Blue = Z).
   - Dragging a gizmo axis moves, rotates, or scales the object with live numerical telemetry and optional snapping (0.5m grid / 15° angle).
3. **Camera & Shading Control**:
   - Touch gestures outside the gizmo allow 1-finger Orbit, 2-finger Pinch-to-Zoom, and 2-finger Drag-to-Pan.
   - Floating camera cube/pill toggles standard orthographic/perspective views (Top, Front, Side, Iso) instantly.
   - Shading switcher cycles seamlessly between **Wireframe**, **Solid Clay**, **Material Preview** (metallic flake paint + clearcoat), and **Studio Render** (multi-light rig + ground reflection).
4. **Scene Hierarchy & Inspector**:
   - Tapping **Outliner** expands a collapsible translucent panel listing all scene nodes with visibility toggles, lock toggles, grouping, and reparenting.
   - Tapping **Inspector** shows exact position/rotation/scale numeric inputs, material color/metallic/roughness sliders, and mesh stats (vertex/face count).
5. **Project Management & History**:
   - Undo and Redo buttons revert and re-apply actions (transforms, adds, deletes, hierarchy changes).
   - Menu provides **New Project**, **Save**, **Save As**, and **Open** (.studio3d JSON format). Background autosave caches state every 30 seconds with automatic recovery prompt on app reload.

### Visual Identity & Theme

- **Palette**:
  - Deep Studio Slate Background: `#0B0D13`
  - Floating HUD Frosted Glass: `#161922` with 85% alpha and subtle `#2A2F3D` borders
  - Primary Accent (Cyan Tech): `#00E5FF`
  - Transform X-Axis: `#FF3B30` (Vibrant Coral Red)
  - Transform Y-Axis: `#34C759` (Electric Emerald Green)
  - Transform Z-Axis: `#007AFF` (Deep Cobalt Blue)
  - Surface Selection Highlight: `#FFD60A` (Bright Gold/Amber)
- **Typography & Ergonomics**:
  - Monospace numeric telemetry for coordinates, angles, and scale factors.
  - Generous touch targets (>48dp) arranged along the left and right screen borders for natural two-thumb thumb-reach in landscape and portrait.

---

## 3. Key Product Decisions & Trade-Offs

- **Rendering Backend: OpenGL ES 3.0 via GLSurfaceView**
  - *Chosen Approach*: Lightweight native OpenGL ES 3.0 with custom GLSL shaders.
  - *Why*: Guaranteed compatibility across all Android versions, lightning-fast compilation, zero external C++ NDK build bloat, and total control over custom rendering passes (wireframe edge shaders, matcaps, PBR-lite car paint, and 3D gizmo ray picking).
  - *Alternatives Considered*: Filament was considered, but requires heavy multi-megabyte C++ binaries and slow Gradle syncs. A 2D Compose Canvas software rasterizer was considered, but lacks hardware depth testing and shader lighting.
- **Scene Graph & Stable Node Identity**
  - *Chosen Approach*: Recursive `SceneNode` tree with unique UUIDs, local and world transformation matrices, bounding boxes, and component mesh/material data.
  - *Why*: Allows true grouping, parent-child hierarchies (e.g. wheels attached to chassis), and clean serialization.
- **State Management & Command Pattern**
  - *Chosen Approach*: Dedicated `CommandHistoryManager` maintaining `UndoableCommand` stacks with deep-copy or delta state snapshots.
  - *Why*: Decouples user actions from the rendering loop; enables infinite, non-destructive undo/redo.
- **Dual-Thumb Input Dispatcher**
  - *Chosen Approach*: Dedicated multi-touch input dispatcher that discriminates between gizmo axis picking (taps/drags on 3D arrows) and viewport navigation (background drags, pinch gestures).
  - *Why*: Prevents annoying camera jumps when attempting to manipulate an object gizmo.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose HUD UI                          │
│ ┌────────────────┐ ┌──────────────────────────┐ ┌────────────────────┐ │
│ │ Top View Bar   │ │ Left: Gizmo & Snapping   │ │ Right: Outliner &  │ │
│ │ Camera Presets │ │ Translate/Rotate/Scale   │ │ Object Inspector   │ │
│ │ Shading Modes  │ │ Grid & Angle Snapping    │ │ Hierarchy & Props  │ │
│ └────────────────┘ └──────────────────────────┘ └────────────────────┘ │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Bottom Bar: Add Primitives, Undo/Redo, Project Save/Open           │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ State / User Actions
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           StudioViewModel                              │
│  - SceneState (Nodes, Selection, Active Tool, Shading Mode)            │
│  - CommandHistoryManager (Undo / Redo stack)                           │
│  - ProjectRepository (Save / Open / Autosave / JSON .studio3d)         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Scene Graph & Render Commands
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Studio3DView (Android GLSurfaceView)                 │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Touch & Gesture Handler (Ray-Gizmo hit test vs Orbit/Pan/Zoom)     │ │
│ └─────────────────────────────────┬──────────────────────────────────┘ │
│                                   │ Direct OpenGL Commands             │
│ ┌─────────────────────────────────▼──────────────────────────────────┐ │
│ │ StudioRenderer (OpenGL ES 3.0)                                     │ │
│ │  - Camera (Perspective / Orthographic, Orbit/Target matrices)      │ │
│ │  - Shading Pipelines: Wireframe, Clay, Material, Studio Render     │ │
│ │  - GizmoRenderer (Screen-depth independent 3D translation handles) │ │
│ │  - GroundGridRenderer (Adaptive infinite grid with axis marks)     │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### Module Structure

- **`com.example.studio3d.math`**: `Vector3`, `Matrix4`, `Quaternion`, `Ray`, `BoundingBox`, and projection/unprojection raycasting utilities.
- **`com.example.studio3d.engine`**:
  - `StudioRenderer`: OpenGL ES 3.0 renderer managing GL state, matrices, and shader programs.
  - `Shaders`: GLSL 300 es shaders for Wireframe, Clay, PBR Material, Studio environment, and Gizmos.
  - `Mesh`: Procedural geometry generators (Cube, Cylinder, Sphere, Wedge, Car Chassis blockout, Wheel, Grid).
  - `Camera`: 6-DOF orbit/pan/zoom camera supporting perspective and orthographic projections with smooth animation to presets (Front, Top, Right, etc.).
  - `Gizmo`: 3D handles for Translation, Rotation, and Scaling with color-coded X/Y/Z axes and ray-box/ray-cylinder collision tests.
- **`com.example.studio3d.scene`**:
  - `SceneNode`: Hierarchy tree node with transform (pos, rot, scale), mesh, material, visibility, and children.
  - `SceneGraph`: Graph root, object lookup, grouping, duplication, and bounding box computation.
  - `Material`: Base color, metallic, roughness, emission, wireframe display toggle.
- **`com.example.studio3d.history`**:
  - `CommandHistory`: Max 50-step undo/redo stack.
  - Commands: `AddNodeCommand`, `DeleteNodeCommand`, `TransformNodeCommand`, `GroupNodesCommand`, `ChangePropertyCommand`.
- **`com.example.studio3d.project`**:
  - `ProjectData`: JSON serialization schema for `.studio3d` files.
  - `ProjectManager`: Save, Save As, Open, Autosave file persistence using Android app storage.
- **`com.example.studio3d.ui`**:
  - `StudioScreen`: Compose root coordinating GLSurfaceView and transparent HUD.
  - `FloatingHUD`: Shading switcher, camera preset cube, transform toolbar.
  - `OutlinerSheet`: Hierarchy drawer with node tree, visibility toggles, and grouping actions.
  - `InspectorSheet`: Real-time coordinate editor, material sliders, and mesh statistics.
  - `AddMeshDialog`: Quick-add menu for primitives and automotive components.
  - `ProjectDialog`: Save / Open / Export dialog with autosave crash recovery.

---

## 5. Verification Plan

1. **Compilation Check**: Run `compile_applet` to verify clean build without warnings or unresolved symbols.
2. **Interactive Testing via Robolectric / UI**:
   - Scene initialization loads automotive blockout correctly.
   - Camera navigates smoothly across Orbit, Pan, and Zoom; presets (Top, Front, Side) snap reliably.
   - Transform gizmos allow dragging along X, Y, and Z axes with snapping.
   - Adding, duplicating, deleting, and grouping nodes updates both the 3D viewport and the Outliner tree.
   - Undo/Redo accurately steps through transform and hierarchy changes.
   - Saving and reopening a project file restores the entire scene hierarchy, materials, and camera state.
