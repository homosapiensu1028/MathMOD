param(
    [Parameter(Mandatory = $false)]
    [string]$PrismInstancePath = ''
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$GradleVersion = '8.12'
$CacheRoot = Join-Path $env:LOCALAPPDATA 'MathProofBuild'
$GradleHome = Join-Path $CacheRoot "gradle-$GradleVersion"
$GradleExe = Join-Path $GradleHome 'bin\gradle.bat'
$DistDir = Join-Path $ProjectRoot 'dist'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java が見つかりません。Minecraft 1.21.1 用の JDK 21 をインストールし、PATH を設定してください。'
}

$JavaVersion = (& java -version 2>&1 | Out-String)
if ($JavaVersion -notmatch 'version "21\.') {
    throw "JDK 21 が必要です。現在の Java:\n$JavaVersion"
}

if (-not (Test-Path $GradleExe)) {
    New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null
    $ZipPath = Join-Path $CacheRoot "gradle-$GradleVersion-bin.zip"
    $Url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    Write-Host "Gradle $GradleVersion をダウンロードしています..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $Url -OutFile $ZipPath
    Expand-Archive -Path $ZipPath -DestinationPath $CacheRoot -Force
    Remove-Item $ZipPath -Force
}

if (-not (Test-Path $GradleExe)) {
    throw "Gradle が見つかりません: $GradleExe"
}

Push-Location $ProjectRoot
try {
    Write-Host 'MathProof をビルドし、テストを実行しています...' -ForegroundColor Cyan
    & $GradleExe --no-daemon clean test build
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

    $BuiltJar = Get-ChildItem (Join-Path $ProjectRoot 'build\libs') -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
        Select-Object -First 1
    if ($null -eq $BuiltJar) { throw 'ビルドは終了しましたが、MOD JAR が見つかりません。' }

    New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
    $OutputJar = Join-Path $DistDir 'mathproof-1.0.0.jar'
    Copy-Item $BuiltJar.FullName $OutputJar -Force

    if (-not [string]::IsNullOrWhiteSpace($PrismInstancePath)) {
        $InstancePath = (Resolve-Path -LiteralPath $PrismInstancePath).Path
        if (Test-Path (Join-Path $InstancePath '.minecraft')) {
            $ModsPath = Join-Path $InstancePath '.minecraft\mods'
        } elseif ((Split-Path $InstancePath -Leaf) -eq '.minecraft') {
            $ModsPath = Join-Path $InstancePath 'mods'
        } else {
            $ModsPath = Join-Path $InstancePath '.minecraft\mods'
        }
        New-Item -ItemType Directory -Force -Path $ModsPath | Out-Null
        Copy-Item $OutputJar (Join-Path $ModsPath 'mathproof-1.0.0.jar') -Force
        Write-Host "Prism インスタンスへインストールしました: $ModsPath" -ForegroundColor Green
    }

    Write-Host ''
    Write-Host 'ビルド成功！' -ForegroundColor Green
    Write-Host "Prism にドラッグする JAR: $OutputJar" -ForegroundColor Green
    Write-Host 'Prism: 対象インスタンスを編集 → Mods → この JAR をドラッグしてください。' -ForegroundColor Yellow
    Start-Process explorer.exe -ArgumentList $DistDir
} finally {
    Pop-Location
}
