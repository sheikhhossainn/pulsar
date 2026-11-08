param([switch]$UseExistingApk)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$work = Join-Path $root '.buildenv'
$sdk = Join-Path $work 'sdk'
$emu = Join-Path $sdk 'emulator\emulator.exe'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-x86_64-debug.apk'
$name = 'Pulsar_PC'
$port = 5582
$serial = "emulator-$port"

if (!(Test-Path -LiteralPath $adb)) { throw "Missing Android platform-tools in $sdk. See README.md, Test on Windows." }
if (!(Test-Path -LiteralPath "$sdk\emulator\.pulsar-package") -or !(Test-Path -LiteralPath "$sdk\system-images\android-35\default\x86_64\.pulsar-package")) {
    Write-Host 'Preparing the Android emulator and virtual phone on this drive...'
    & python (Join-Path $PSScriptRoot 'prepare_pc_emulator.py')
    if ($LASTEXITCODE) { throw 'Android emulator setup failed. Check the download message above.' }
}
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_EMULATOR_HOME = Join-Path $work 'android-user'
$env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_EMULATOR_HOME 'avd'
$env:GRADLE_USER_HOME = Join-Path $work 'cache'
$env:TEMP = Join-Path $work 'pc-temp'
$env:TMP = $env:TEMP
$avdDir = Join-Path $env:ANDROID_AVD_HOME "$name.avd"
$logDir = Join-Path $work 'pc-logs'
foreach ($dir in @($avdDir, $logDir, $env:TEMP)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
$locator = Join-Path $env:ANDROID_AVD_HOME "$name.ini"
@("avd.ini.encoding=UTF-8", "path=$avdDir", "target=android-35") | Set-Content -LiteralPath $locator -Encoding UTF8
$config = Join-Path $avdDir 'config.ini'
if (!(Test-Path -LiteralPath $config)) {
    @(
        "AvdId=$name", 'avd.ini.displayname=Pulsar PC', 'abi.type=x86_64', 'hw.cpu.arch=x86_64',
        'hw.cpu.ncore=2', 'hw.ramSize=2048', 'vm.heapSize=256', 'hw.lcd.width=720',
        'hw.lcd.height=1520', 'hw.lcd.density=300', 'hw.keyboard=yes', 'hw.mainKeys=no',
        'hw.audioInput=yes', 'hw.audioOutput=yes', 'hw.gpu.enabled=yes', 'hw.gpu.mode=auto',
        'hw.camera.back=none', 'hw.camera.front=none', 'disk.dataPartition.size=3G',
        'image.sysdir.1=system-images/android-35/default/x86_64/', 'tag.id=default',
        'tag.display=Default', 'PlayStore.enabled=false', 'showDeviceFrame=no', 'skin.dynamic=yes'
    ) | Set-Content -Encoding ASCII -LiteralPath $config
}
function Invoke-Device {
    & $adb -s $serial @args
    if ($LASTEXITCODE) { throw "Android command failed: $args" }
}
function Confirm-Device {
    $deviceName = & $adb -s $serial emu avd name
    if ($LASTEXITCODE -or $deviceName -notcontains $name) {
        throw "Port $port is occupied by a different emulator. Close that emulator and retry."
    }
}
& $adb start-server
if ($LASTEXITCODE) { throw 'Could not start the Android debug bridge.' }
if (!$UseExistingApk) {
    Write-Host 'Building the latest Android source...'
    if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath "$env:JAVA_HOME\bin\javac.exe")) {
        $jdk = @('C:\Program Files\Java\jdk-18.0.2.1', 'C:\Program Files\Android\Android Studio\jbr') | Where-Object {Test-Path -LiteralPath "$_\bin\javac.exe"} | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk }
    }
    $gradle = Join-Path $work 'gradle\bin\gradle.bat'
    if (!(Test-Path -LiteralPath $gradle)) { $gradle = Join-Path $root 'gradlew.bat' }
    & $gradle --offline --no-daemon --console=plain :app:assembleDebug
    if ($LASTEXITCODE) { throw 'Build failed. For the last built APK, use -UseExistingApk.' }
}
if (!(Test-Path -LiteralPath $apk)) { throw "Missing x86_64 APK: $apk" }
$devices = & $adb devices
$attached = $devices | Where-Object { $_ -match "^$serial\s" }
if (!$attached) {
    Write-Host 'Starting your Pulsar virtual phone...'
    $emulatorProcess = Start-Process -FilePath $emu -ArgumentList @('-avd',$name,'-port',"$port",'-no-boot-anim','-gpu','auto','-feature','-Vulkan','-no-snapshot','-no-metrics') -PassThru -WorkingDirectory $root -RedirectStandardOutput "$logDir\emulator.log" -RedirectStandardError "$logDir\emulator-error.log"
} elseif ($attached -match '\sdevice$') {
    Confirm-Device
}
Write-Host 'Waiting for Android to finish starting...'
$deadline = (Get-Date).AddMinutes(6)
do {
    $online = (& $adb devices) | Where-Object { $_ -match "^$serial\s+device$" }
    if ($online -and ((& $adb -s $serial shell getprop sys.boot_completed) -join '').Trim() -eq '1') { break }
    if ($emulatorProcess -and $emulatorProcess.HasExited -and !$online) {
        throw "The emulator exited with code $($emulatorProcess.ExitCode). See $logDir\emulator-error.log and emulator.log."
    }
    if ((Get-Date) -gt $deadline) { throw "Android did not finish booting. See $logDir\emulator.log." }
    Start-Sleep -Seconds 2
} while ($true)
Confirm-Device
Invoke-Device shell input keyevent KEYCODE_WAKEUP
Invoke-Device shell wm dismiss-keyguard
Write-Host 'Installing Pulsar while keeping saved data...'
Invoke-Device install -r $apk
Invoke-Device shell am start -S -W -n bd.pulsar/.MainActivity
Write-Host 'Pulsar is open. Run this launcher again after making changes.'
