# ============================================================
#  Apex3D - Fast Build & Run Script
#  Pakai: .\dev-run.ps1            (build + install + run)
#         .\dev-run.ps1 -InstallOnly $false -NoBuild $true
#  Opsi:
#    -NoBuild      : skip gradle build, langsung install APK yang sudah ada
#    -NoInstall    : hanya build tanpa install
#    -NoLaunch     : install tapi tidak membuka aplikasi
# ============================================================
param(
    [switch]$NoBuild,
    [switch]$NoInstall,
    [switch]$NoLaunch
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$Apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
$Package = "com.aistudio.apex3d.kxzmw"
$Activity = "com.aistudio.apex3d.kxzmw/com.example.MainActivity"
$Devices = @("e280076a0404")

Set-Location $ProjectRoot

# ---------- [1] BUILD (Gradle daemon tetap hidup = cepat) ----------
if (-not $NoBuild) {
    if (-not (Test-Path $Apk)) {
        Write-Host "[1/3] BUILD PERTAMA (assembleDebug)..." -ForegroundColor Cyan
        .\gradlew.bat :app:assembleDebug --console=plain
    }
    else {
        Write-Host "[1/3] BUILD INCREMENTAL (assembleDebug)..." -ForegroundColor Cyan
        .\gradlew.bat :app:assembleDebug --console=plain
    }
    if ($LASTEXITCODE -ne 0) { Write-Host "BUILD GAGAL!" -ForegroundColor Red; exit 1 }
} else {
    Write-Host "[1/3] BUILD DI-LEWATI (-NoBuild)" -ForegroundColor Yellow
}

# ---------- [2] INSTALL (langsung adb install, bukan gradle) ----------
if (-not $NoInstall) {
    if (-not (Test-Path $Apk)) { Write-Host "APK tidak ditemukan!" -ForegroundColor Red; exit 1 }

    Write-Host "[2/3] INSTALL APK via adb (1 serial saja = cepat)..." -ForegroundColor Cyan
    foreach ($dev in $Devices) {
        Write-Host "   -> $dev" -ForegroundColor Gray
        adb -s $dev install -r -t --no-streaming $Apk
        if ($LASTEXITCODE -ne 0) {
            Write-Host "   Install gagal, mencoba via 'pm install' (bypass verifier)..." -ForegroundColor Yellow
            adb -s $dev push $Apk /data/local/tmp/apex3d.apk
            adb -s $dev shell "pm install -r -t /data/local/tmp/apex3d.apk"
            adb -s $dev shell "rm /data/local/tmp/apex3d.apk"
        }
    }
} else {
    Write-Host "[2/3] INSTALL DI-LEWATI (-NoInstall)" -ForegroundColor Yellow
}

# ---------- [3] LAUNCH (langsung buka aplikasi) ----------
if (-not $NoLaunch) {
    Write-Host "[3/3] MELUNCURKAN $Package ..." -ForegroundColor Cyan
    adb -s $Devices[0] shell am start -S -n $Activity
}

Write-Host "SELESAI ✅" -ForegroundColor Green
