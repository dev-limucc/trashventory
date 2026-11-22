# Builds Trashventory for every supported Minecraft version and collects jars into dist\
# Usage:  .\build-all.ps1
$ErrorActionPreference = "Stop"

# ALWAYS force the ATLauncher JDK 25: the system-wide JAVA_HOME points at Microsoft JDK 21,
# which cannot compile the 26.x targets ("release version 25 not supported").
$env:JAVA_HOME = "C:\Users\Windows 11\AppData\Roaming\ATLauncher\runtimes\minecraft\java-runtime-epsilon\windows-x64\java-runtime-epsilon"

$targets = @('26.1.2', '26.2', '26.3')
$failed = @()

foreach ($t in $targets) {
    Write-Host "=== Building target $t ===" -ForegroundColor Cyan
    & .\gradlew.bat --no-daemon build "-Ptarget=$t" --console=plain -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED: $t" -ForegroundColor Red
        $failed += $t
    }
}

$modVersion = ((Get-Content gradle.properties | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', '').Trim()
New-Item -ItemType Directory -Force dist | Out-Null
Remove-Item dist\*.jar -ErrorAction SilentlyContinue   # dist holds only the current version
Copy-Item "build\libs\trashventory-$modVersion+mc*.jar" dist\ -Force -Exclude "*-sources.jar"
Write-Host "`nJars in dist\:" -ForegroundColor Green
Get-ChildItem dist\*.jar | Select-Object -ExpandProperty Name

if ($failed.Count -gt 0) {
    Write-Host "`nFailed targets: $($failed -join ', ')" -ForegroundColor Red
    exit 1
}
