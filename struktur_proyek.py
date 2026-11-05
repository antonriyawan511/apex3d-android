# ============================================================
#  Peta Struktur Proyek — tampilan ala "Android View" Android Studio
#  Murni Python standar (tkinter), tanpa dependency eksternal.
#  Tema: monokrom hitam-putih.
#  Fitur:
#   - Folder picker untuk mengganti root proyek
#   - Android View (manifests / java / res / Gradle Scripts)
#     bila root terdeteksi proyek Android; otomatis fallback
#     ke Full View (struktur folder biasa) untuk proyek lain.
#   - Double-click file = buka file; double-click folder = buka di Explorer.
# ============================================================

import json
import os
import subprocess
import sys
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

# ---------------- Konstanta ----------------
BUILD_FILES = ("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")
GRADLE_FILE_NAMES = set(BUILD_FILES) | {
    "gradle.properties", "local.properties", "gradlew", "gradlew.bat",
    "gradle-wrapper.jar", "gradle-wrapper.properties",
}
IGNORE_DIRS = {
    ".gradle", ".git", ".idea", "build", ".kotlin", "__pycache__",
    "node_modules", ".aistudio", "venv", ".venv", ".vscode",
}
MODULE_ANDROID_ROOTS = ("src/main", "src")


# ---------------- Model pohon ----------------
class Node:
    """Satu baris pada pohon tampilan."""

    def __init__(self, name, is_dir, path):
        self.name = name
        self.is_dir = is_dir
        self.path = path
        self.children = []


def add(node, name, is_dir, path):
    child = Node(name, is_dir, path)
    node.children.append(child)
    return child


def scan_dir_sorted(path):
    """Entry dalam folder: folder dulu, lalu file, alfabetis; abaikan folder sampah/tersembunyi."""
    result = []
    try:
        entries = list(os.scandir(path))
    except OSError:
        return result
    for e in entries:
        if e.is_dir(follow_symlinks=False) and (e.name in IGNORE_DIRS or e.name.startswith(".")):
            continue
        result.append(e)
    result.sort(key=lambda x: (not x.is_dir(follow_symlinks=False), x.name.lower()))
    return result


def recursive_dir_node(base):
    node = Node(os.path.basename(base) or base, True, base)
    for e in scan_dir_sorted(base):
        if e.is_dir(follow_symlinks=False):
            node.children.append(recursive_dir_node(e.path))
        else:
            add(node, e.name, False, e.path)
    return node


def collapse_empty(node):
    """Buang folder logis yang tidak ada isinya (kasih tampilan bersih)."""
    node.children = [c for c in node.children if not c.is_dir or c.children]
    for c in node.children:
        collapse_empty(c)
    return node


def flatten_chain(node):
    """Paket satu-arah diratakan: 'com' > 'example' menjadi satu baris 'com.example'."""
    if (len(node.children) == 1 and node.children[0].is_dir
            and node.children[0].path is None):
        child = node.children[0]
        flatten_chain(child)
        node.name = f"{node.name}.{child.name}"
        node.children = child.children
    return node


# ---------------- View ala Android Studio ----------------
def is_android_project(root):
    if not os.path.isdir(root):
        return False
    for d in os.listdir(root):
        p = os.path.join(root, d)
        if not os.path.isdir(p) or d in IGNORE_DIRS or d.startswith("."):
            continue
        if any(os.path.isfile(os.path.join(p, bf)) for bf in BUILD_FILES):
            return True
        src_main = os.path.join(p, "src", "main")
        if os.path.isdir(src_main) and os.path.isfile(os.path.join(src_main, "AndroidManifest.xml")):
            return True
    return False


def detect_modules(root):
    mods = [
        d for d in os.listdir(root)
        if os.path.isdir(os.path.join(root, d))
        and d not in IGNORE_DIRS and not d.startswith(".")
        and os.path.isfile(os.path.join(root, d, "src", "main", "AndroidManifest.xml"))
    ]
    if not mods:
        mods = [
            d for d in os.listdir(root)
            if os.path.isdir(os.path.join(root, d))
            and d not in IGNORE_DIRS and not d.startswith(".")
            and any(os.path.isfile(os.path.join(root, d, bf)) for bf in BUILD_FILES)
        ]
    if not mods:
        # Satu modul tunggal di root itu sendiri (misal project-nya bukan multi-modul)
        if os.path.isfile(os.path.join(root, "src", "main", "AndroidManifest.xml")):
            mods = [root]
    return sorted(mods)


def android_manifests_node(root, module_dir):
    label = "manifests" if module_dir != root else "manifests"
    node = Node(label, True, os.path.join(module_dir, "src", "main"))
    main_dir = os.path.join(module_dir, "src", "main")
    if os.path.isdir(main_dir):
        for e in sorted(os.scandir(main_dir), key=lambda x: x.name.lower()):
            if e.is_file() and e.name.lower().endswith(".xml"):
                add(node, e.name, False, e.path)
    return node


def android_java_node(module_dir):
    """File .java/.kt dari src/main/java + src/main/kotlin, dikelompokkan per paket."""
    node = Node("java", True, None)
    bases = [
        os.path.join(module_dir, "src", "main", "java"),
        os.path.join(module_dir, "src", "main", "kotlin"),
    ]
    files = []
    for base in bases:
        if not os.path.isdir(base):
            continue
        for dirpath, _dirs, filenames in os.walk(base):
            for f in filenames:
                if f.endswith((".java", ".kt", ".kts")):
                    full = os.path.join(dirpath, f)
                    rel = os.path.relpath(full, base)
                    files.append((rel.replace("\\", "/"), full))
    files.sort(key=lambda t: t[0].lower())

    for rel, full in files:
        parts = rel.split("/")
        cur = node
        for part in parts[:-1]:  # folder paket
            existing = next((c for c in cur.children if c.is_dir and c.name == part), None)
            if existing is None:
                cur = add(cur, part, True, None)
            else:
                cur = existing
        add(cur, parts[-1], False, full)
    return node


def android_res_node(module_dir):
    """res/ dikelompokkan per tipe: drawable, layout, mipmap, values, ..."""
    res_dir = os.path.join(module_dir, "src", "main", "res")
    node = Node("res", True, res_dir)
    if os.path.isdir(res_dir):
        for e in scan_dir_sorted(res_dir):
            g = add(node, e.name, e.is_dir(follow_symlinks=False), e.path)
            if e.is_dir(follow_symlinks=False):
                for s in scan_dir_sorted(e.path):
                    if s.is_dir(follow_symlinks=False):
                        g.children.append(recursive_dir_node(s.path))
                    else:
                        add(g, s.name, False, s.path)
    return node


def module_android_node(root, module_dir):
    name = "app" if module_dir == root else os.path.basename(module_dir)
    node = Node(name, True, module_dir)
    node.children.append(android_manifests_node(root, module_dir))
    java_node = collapse_empty(android_java_node(module_dir))
    java_node.children = [flatten_chain(c) for c in java_node.children]
    node.children.append(java_node)
    node.children.append(collapse_empty(android_res_node(module_dir)))
    assets_dir = os.path.join(module_dir, "src", "main", "assets")
    if os.path.isdir(assets_dir):
        assets_node = recursive_dir_node(assets_dir)
        assets_node.name = "assets"
        node.children.append(assets_node)
    return node


def gradle_scripts_node(root, modules):
    node = Node("Gradle Scripts", True, root)
    if not os.path.isdir(root):
        return node

    def is_gradle_file(name):
        return name in GRADLE_FILE_NAMES

    # File build milik root (Project)
    for e in sorted(os.listdir(root), key=str.lower):
        p = os.path.join(root, e)
        if os.path.isfile(p) and is_gradle_file(e):
            label = f"{e} (Project)" if e in BUILD_FILES else e
            add(node, label, False, p)
    # File build milik tiap modul
    for m in modules:
        mdir = m if os.path.isabs(m) else os.path.join(root, m)
        mname = "app" if mdir == root else os.path.basename(mdir)
        for bf in BUILD_FILES:
            p = os.path.join(mdir, bf)
            if os.path.isfile(p):
                add(node, f"{bf} ({mname})", False, p)
    # gradle/wrapper
    wrapper = os.path.join(root, "gradle", "wrapper")
    if os.path.isdir(wrapper):
        w = add(node, "gradle-wrapper", True, wrapper)
        for e in scan_dir_sorted(wrapper):
            add(w, e.name, False, e.path)
    return node


def build_android_view(root):
    root_node = Node(os.path.basename(root) or root, True, root)
    modules = detect_modules(root)
    for m in modules:
        mdir = m if os.path.isabs(m) else os.path.join(root, m)
        root_node.children.append(module_android_node(root, mdir))
    root_node.children.append(gradle_scripts_node(root, modules))
    return root_node


def build_full_view(root):
    """Struktur penuh sederhana (basis folder)."""
    node = Node(os.path.basename(root) or root, True, root)
    for e in scan_dir_sorted(root):
        if e.is_dir(follow_symlinks=False):
            node.children.append(recursive_dir_node(e.path))
        else:
            add(node, e.name, False, e.path)
    return node


def build_tree(root, prefer_android=True):
    if prefer_android and is_android_project(root):
        return build_android_view(root), True
    return build_full_view(root), False


# ---------------- root awal ----------------
def guess_project_root():
    cwd = os.path.dirname(os.path.abspath(__file__))
    ident = os.path.join(cwd, ".project_identity.json")
    if os.path.isfile(ident):
        try:
            with open(ident, "r", encoding="utf-8", errors="ignore") as f:
                data = json.load(f)
            for key in ("projectPath", "project_path", "root", "path"):
                val = data.get(key)
                if isinstance(val, str) and os.path.isdir(val):
                    return val
        except Exception:
            pass
    return cwd


# ---------------- GUI ----------------
class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Peta Struktur Proyek")
        self.configure(bg="#0b0b0b")
        self.geometry("700x540")
        self.minsize(420, 320)
        self.node_paths = {}  # iid -> Node

        self._build_style()
        self._build_ui()

        self.directory_entry.insert(0, guess_project_root())
        self.refresh()

    # ---------- tema monokrom ----------
    def _build_style(self):
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure(
            "Mono.Treeview", background="#0b0b0b", foreground="#eaeaea",
            fieldbackground="#0b0b0b", rowheight=20, font=("Consolas", 10),
            borderwidth=0,
        )
        style.map(
            "Mono.Treeview",
            background=[("selected", "#eaeaea")],
            foreground=[("selected", "#0b0b0b")],
        )
        style.configure(
            "Mono.Treeview.Heading", background="#1d1d1d", foreground="#cfcfcf",
            font=("Consolas", 9, "bold"),
        )
        style.configure("TScrollbar",
                        background="#1a1a1a", troughcolor="#0b0b0b",
                        bordercolor="#0b0b0b", arrowcolor="#eaeaea")

    def _mk_button(self, parent, text, cmd):
        return tk.Button(
            parent, text=text, command=cmd,
            bg="#0b0b0b", fg="#eaeaea", activebackground="#eaeaea",
            activeforeground="#0b0b0b", relief="solid", bd=1,
            font=("Consolas", 9, "bold"), padx=7, pady=1, cursor="hand2",
        )

    def _mk_radio(self, parent, text, value):
        return tk.Radiobutton(
            parent, text=text, value=value, variable=self.mode_var,
            bg="#0b0b0b", fg="#eaeaea", selectcolor="#0b0b0b",
            activebackground="#0b0b0b", activeforeground="#eaeaea",
            font=("Consolas", 9, "bold"), anchor="w", padx=2, bd=0,
            highlightthickness=0, cursor="hand2",
        )

    # ---------- widget ----------
    def _build_ui(self):
        bar = tk.Frame(self, bg="#0b0b0b")
        bar.pack(fill="x", padx=10, pady=(10, 4))

        self.directory_entry = tk.Entry(
            bar, bg="#111111", fg="#eaeaea", insertbackground="#eaeaea",
            relief="flat", font=("Consolas", 9),
        )
        self.directory_entry.pack(side="left", fill="x", expand=True, ipady=2, padx=(0, 6))
        self.directory_entry.bind("<Return>", lambda _e: self.refresh())

        self._mk_button(bar, "Pilih Folder", self.pick_folder).pack(side="left", padx=2)
        self._mk_button(bar, "Refresh", self.refresh).pack(side="left", padx=2)
        self._mk_button(bar, "Naik Folder", self.go_up).pack(side="left", padx=2)

        bar2 = tk.Frame(self, bg="#0b0b0b")
        bar2.pack(fill="x", padx=10, pady=(0, 6))
        self.mode_var = tk.StringVar(value="android")
        self._mk_radio(bar2, "Android View", "android").pack(side="left", padx=(0, 12))
        self._mk_radio(bar2, "Full View", "full").pack(side="left")
        self.mode_var.trace_add("write", lambda *_a: self.refresh())
        self._mk_button(bar2, "Jadikan Root", self.use_selected_as_root).pack(side="right", padx=2)
        self._mk_button(bar2, "Buka di Explorer", self.open_current_folder).pack(side="right", padx=2)

        wrap = tk.Frame(self, bg="#0b0b0b")
        wrap.pack(fill="both", expand=True, padx=10, pady=6)

        self.tree = ttk.Treeview(wrap, style="Mono.Treeview", show="tree", selectmode="browse")
        ysb = ttk.Scrollbar(wrap, orient="vertical", command=self.tree.yview)
        xsb = ttk.Scrollbar(wrap, orient="horizontal", command=self.tree.xview)
        self.tree.configure(yscrollcommand=ysb.set, xscrollcommand=xsb.set)
        self.tree.grid(row=0, column=0, sticky="nsew")
        ysb.grid(row=0, column=1, sticky="ns")
        xsb.grid(row=1, column=0, sticky="ew")
        wrap.rowconfigure(0, weight=1)
        wrap.columnconfigure(0, weight=1)
        try:  # indent pohon lebih sempit agar muat tanpa fullscreen
            self.tree.configure(indent=10)
        except tk.TclError:
            pass

        self.status = tk.Label(
            self, bg="#0b0b0b", fg="#9a9a9a", font=("Consolas", 8),
            anchor="w", text="Siap.",
        )
        self.status.pack(fill="x", side="bottom", padx=10, pady=(0, 8))

        self.tree.bind("<<TreeviewSelect>>", self._on_select)
        self.tree.bind("<Double-1>", self.on_double_click)
        self.tree.bind("<3>", self.show_context_menu)

        self.ctx_menu = tk.Menu(self, tearoff=0, bg="#1a1a1a", fg="#eaeaea")
        self.ctx_menu.add_command(label="Copy path", command=self.copy_selected_path)
        self.ctx_menu.add_command(label="Jadikan root", command=self.use_selected_as_root)
        self.ctx_menu.add_command(label="Buka dengan aplikasi default", command=self.open_selected_file)

    # ---------- aksi ----------
    def pick_folder(self):
        current = self.directory_entry.get().strip() or os.getcwd()
        chosen = filedialog.askdirectory(initialdir=current, title="Pilih folder root proyek")
        if chosen:
            self.directory_entry.delete(0, "end")
            self.directory_entry.insert(0, chosen)
            self.refresh()

    def go_up(self):
        current = self.directory_entry.get().strip()
        if current and os.path.isdir(current):
            up = os.path.dirname(os.path.normpath(current))
            if up and up != current:
                self.directory_entry.delete(0, "end")
                self.directory_entry.insert(0, up)
                self.refresh()

    def refresh(self):
        root = self.directory_entry.get().strip()
        if not root or not os.path.isdir(root):
            self._set_status("Folder tidak ditemukan.")
            return
        self.node_paths.clear()
        self.tree.delete(*self.tree.get_children())

        data, is_android = build_tree(root, prefer_android=(self.mode_var.get() == "android"))
        self._populate("", data, open_depth=2)
        label = "Android View ala Android Studio" if is_android else "Full View (proyek non-Android)"
        self._set_status(f"Root: {root} | Mode: {label}")

    def _populate(self, parent, node, open_depth):
        iid = self.tree.insert(parent, "end", text=node.name,
                               open=open_depth > 0)
        self.node_paths[iid] = node
        for child in node.children:
            self._populate(iid, child, open_depth - 1)

    def _on_select(self, _event=None):
        node = self.node_paths.get(self.tree.focus())
        if node is None:
            return
        tipe = "folder" if node.is_dir else "file"
        self._set_status(f"[{tipe}] {node.path}")

    def on_double_click(self, _event):
        node = self.node_paths.get(self.tree.focus())
        if node is None:
            return
        if node.is_dir and node.path and os.path.isdir(node.path):
            self.open_in_explorer(node.path)
        elif node.path and os.path.isfile(node.path):
            self.open_selected_file()

    def show_context_menu(self, event):
        iid = self.tree.identify_row(event.y)
        if iid:
            self.tree.selection_set(iid)
            self.ctx_menu.tk_popup(event.x_root, event.y_root)

    def use_selected_as_root(self):
        node = self.node_paths.get(self.tree.focus())
        if node and node.is_dir and node.path and os.path.isdir(node.path):
            self.directory_entry.delete(0, "end")
            self.directory_entry.insert(0, node.path)
            self.refresh()

    def open_current_folder(self):
        node = self.node_paths.get(self.tree.focus())
        if node and node.path and os.path.exists(node.path):
            target = node.path if node.is_dir else os.path.dirname(node.path)
        else:
            target = self.directory_entry.get().strip()
        if target and os.path.isdir(target):
            self.open_in_explorer(target)

    def open_selected_file(self):
        node = self.node_paths.get(self.tree.focus())
        if node is None or not node.path or not os.path.isfile(node.path):
            return
        try:
            if sys.platform.startswith("win"):
                os.startfile(node.path)  # type: ignore[attr-defined]
            elif sys.platform == "darwin":
                subprocess.run(["open", node.path], check=False)
            else:
                subprocess.run(["xdg-open", node.path], check=False)
        except OSError as err:
            messagebox.showerror("Gagal membuka", str(err))

    def copy_selected_path(self):
        node = self.node_paths.get(self.tree.focus())
        if node is None:
            return
        self.clipboard_clear()
        self.clipboard_append(node.path or "")
        self._set_status(f"Copied: {node.path}")

    def open_in_explorer(self, path):
        try:
            if sys.platform.startswith("win"):
                os.startfile(path)  # type: ignore[attr-defined]
            elif sys.platform == "darwin":
                subprocess.run(["open", path], check=False)
            else:
                subprocess.run(["xdg-open", path], check=False)
        except OSError as err:
            messagebox.showerror("Gagal membuka folder", str(err))

    def _set_status(self, text):
        self.status.config(text=text)


if __name__ == "__main__":
    app = App()
    app.mainloop()
