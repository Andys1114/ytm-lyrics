[CmdletBinding()]
param([switch]$RebuildRenderer)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$oldJavaOptions = $env:JAVA_TOOL_OPTIONS
Push-Location -LiteralPath $root
try {
    if ($RebuildRenderer) {
        & npm.cmd --prefix demo run build
        if ($LASTEXITCODE -ne 0) { throw 'AMLL 本地资源生成失败，未构建 APK。' }
        & npm.cmd --prefix demo test
        if ($LASTEXITCODE -ne 0) { throw 'AMLL 时间轴与桥接测试失败，未构建 APK。' }
    }
    New-Item -ItemType Directory -Force '.tooling', '.tooling/socket-tmp', 'dist' | Out-Null
    # Windows JDKs use this property (not java.io.tmpdir) for NIO socket files.
    $socketDir = Join-Path $root '.tooling/socket-tmp'
    $env:JAVA_TOOL_OPTIONS = "$oldJavaOptions `"-Djdk.net.unixdomain.tmpdir=$socketDir`"".Trim()
    if (-not (Test-Path -LiteralPath '.tooling/debug.keystore')) {
        $keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/keytool.exe' } else { 'keytool' }
        & $keytool -genkeypair -keystore '.tooling/debug.keystore' -storepass android `
            -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' `
            -keyalg RSA -keysize 2048 -validity 10000
        if ($LASTEXITCODE -ne 0) { throw '无法创建自用调试签名。请检查 JDK 的 keytool。' }
    }
    $gradle = Join-Path $root 'gradlew.bat'
    & $gradle :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console plain
    if ($LASTEXITCODE -ne 0) { throw '构建或检查失败，未更新 dist 中的 APK。' }
    $metadata = Get-Content -LiteralPath 'app/build/outputs/apk/debug/output-metadata.json' -Raw | ConvertFrom-Json
    $artifact = "dist/ytm-lyrics-$($metadata.elements[0].versionName)-debug.apk"
    Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination $artifact -Force
    Write-Host "APK: $(Join-Path $root $artifact)"
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldJavaOptions
    Pop-Location
}
