param([string]$JavaHome, [string]$AndroidSdk, [switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (!$JavaHome) {
        $candidates = @(
            'D:\Program Files\Android\Android Studio\jbr',
            'C:\Program Files\Android\Android Studio\jbr',
            $env:JAVA_HOME24,
            $env:JAVA_HOME
        )
        $JavaHome = $candidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe')) } | Select-Object -First 1
    }
    if (!$JavaHome) { throw 'Set -JavaHome to a JDK 17-25 installation.' }
    $env:JAVA_HOME = $JavaHome
    if (!$AndroidSdk) {
        $AndroidSdk = @($env:ANDROID_HOME, 'D:\ProgramData\Android\Sdk', "$env:LOCALAPPDATA\Android\Sdk") | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platforms')) } | Select-Object -First 1
    }
    if (!$AndroidSdk) { throw 'Set -AndroidSdk to an Android SDK with API 37 and Build Tools 36.0.0.' }
    $env:ANDROID_HOME = $AndroidSdk
    $env:ANDROID_USER_HOME = (New-Item -ItemType Directory -Path '.android-user' -Force).FullName
    $env:GRADLE_USER_HOME = (New-Item -ItemType Directory -Path '.gradle-user' -Force).FullName
    $tasks = @('assembleDebug', 'lintDebug')
    if (!$SkipTests) { $tasks += 'testDebugUnitTest' }
    if (Test-Path -LiteralPath '.tools\gradle-9.3.1\bin\gradle.bat') {
        & '.tools\gradle-9.3.1\bin\gradle.bat' @tasks --no-daemon
    } else {
        & '.\gradlew.bat' @tasks --no-daemon
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
    New-Item -ItemType Directory -Path 'dist' -Force | Out-Null
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'dist\weekday-debug.apk' -Force
    Write-Output 'APK: dist\weekday-debug.apk'
} finally {
    Pop-Location
}
