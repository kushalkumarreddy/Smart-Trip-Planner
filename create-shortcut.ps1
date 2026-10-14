$WshShell = New-Object -ComObject WScript.Shell
$desktopPath = [System.Environment]::GetFolderPath('Desktop')
$shortcutPath = Join-Path $desktopPath 'VoyageCraft.lnk'
$targetBatch = Join-Path $PSScriptRoot 'run-voyagecraft.bat'

$shortcut = $WshShell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $targetBatch
$shortcut.WorkingDirectory = $PSScriptRoot
$shortcut.Description = 'VoyageCraft: AI Smart Travel Planner & Route Optimizer'
$shortcut.IconLocation = 'shell32.dll,13'
$shortcut.Save()

Write-Host "Desktop shortcut created successfully at: $shortcutPath"
Write-Host "Target: $targetBatch"
