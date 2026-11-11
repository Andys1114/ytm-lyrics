[CmdletBinding()]
param(
    [switch]$RebuildRenderer,
    [switch]$Catalog,
    [ValidatePattern('^(-[a-z0-9]+)*$')][string]$ArtifactSuffix = ''
)
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
    if (-not $Catalog -and -not (Test-Path -LiteralPath '.tooling/debug.keystore')) {
        $keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/keytool.exe' } else { 'keytool' }
        & $keytool -genkeypair -keystore '.tooling/debug.keystore' -storepass android `
            -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' `
            -keyalg RSA -keysize 2048 -validity 10000
        if ($LASTEXITCODE -ne 0) { throw '无法创建自用调试签名。请检查 JDK 的 keytool。' }
    }
    $gradle = Join-Path $root 'gradlew.bat'
    $variant = if ($Catalog) { 'Release' } else { 'Debug' }
    & $gradle :core:check :app:testDebugUnitTest ":app:lint$variant" ":app:assemble$variant" --no-daemon --console plain
    if ($LASTEXITCODE -ne 0) { throw '构建或检查失败，未更新 dist 中的 APK。' }
    $output = 'app/build/outputs/apk/' + $variant.ToLowerInvariant()
    $metadata = Get-Content -LiteralPath (Join-Path $output 'output-metadata.json') -Raw | ConvertFrom-Json
    $apk = $metadata.elements[0]
    if ($Catalog) {
        if ($metadata.applicationId -cne 'io.github.andys1114.ytmlyrics' -or $apk.outputFile -cne 'app-release-unsigned.apk') {
            throw '目录构建应输出 io.github.andys1114.ytmlyrics 的未签名 APK。'
        }
        $tag = "$($apk.versionCode)-$($apk.versionName)"
        $artifact = "dist/ytm-lyrics-$tag${ArtifactSuffix}-unsigned.apk"
        Write-Host "目录 Release：tag=$tag；title=$($apk.versionName)；签名后再上传。"
    } else {
        $artifact = "dist/ytm-lyrics-$($apk.versionName)${ArtifactSuffix}-debug.apk"
    }
    Copy-Item -LiteralPath (Join-Path $output $apk.outputFile) -Destination $artifact -Force
    Write-Host "APK: $(Join-Path $root $artifact)"
} finally {
    $env:JAVA_TOOL_OPTIONS = $oldJavaOptions
    Pop-Location
}
