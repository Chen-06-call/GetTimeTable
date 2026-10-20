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
    if (!$JavaHome) { throw '请使用 -JavaHome 指定 JDK 17–25（建议 Android Studio 内置 JDK）。' }
    $env:JAVA_HOME = $JavaHome
    if (!$AndroidSdk) {
        $AndroidSdk = @($env:ANDROID_HOME, 'D:\ProgramData\Android\Sdk', "$env:LOCALAPPDATA\Android\Sdk") | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platforms')) } | Select-Object -First 1
    }
    if (!$AndroidSdk) { throw '请用 -AndroidSdk 指定 Android SDK（需要 API 37 平台和 Build Tools 36.0.0）。' }
    $env:ANDROID_HOME = $AndroidSdk
    $env:ANDROID_USER_HOME = (New-Item -ItemType Directory -Path '.android-user' -Force).FullName
    $env:GRADLE_USER_HOME = (New-Item -ItemType Directory -Path '.gradle-user' -Force).FullName
    $tasks = @('assembleRelease', 'lintRelease')
    if (!$SkipTests) { $tasks += 'testDebugUnitTest' }
    if (Test-Path -LiteralPath '.tools\gradle-9.3.1\bin\gradle.bat') {
        & '.tools\gradle-9.3.1\bin\gradle.bat' @tasks --no-daemon
    } else {
        & '.\gradlew.bat' @tasks --no-daemon
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle 失败，退出码 $LASTEXITCODE。" }
    New-Item -ItemType Directory -Path 'dist' -Force | Out-Null
    Copy-Item -LiteralPath 'app\build\outputs\apk\release\app-release.apk' -Destination 'dist\weekday-release.apk' -Force
    Write-Output 'APK: dist\weekday-release.apk'
} finally {
    Pop-Location
}
