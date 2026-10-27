# Opens BOFO Finder in 4 window sizes side by side (small phone, normal phone, big phone, tablet).
# Run from PowerShell:   powershell -ExecutionPolicy Bypass -File dev\preview-sizes.ps1
# Change the list below to test other sizes. Format: "WIDTHxHEIGHT@LEFT,TOP" (LEFT/TOP = where the window opens on screen).
$sizes = @(
  "320x568@0,10",
  "360x720@335,10",
  "412x860@710,10",
  "800x900@1135,10"
)
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
Write-Host "Building once..."
mvn -q compile
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed. Fix the error above, then run this again."; exit 1 }
foreach ($s in $sizes) {
  Write-Host "Opening $s"
  Start-Process -FilePath "mvn.cmd" -ArgumentList @("javafx:run", "-Djavafx.args=--size=$s") -WorkingDirectory $root -WindowStyle Minimized
  Start-Sleep -Seconds 8      # one at a time so they do not fight over the build folder
}
Write-Host "Done. Close the windows when you are finished."
