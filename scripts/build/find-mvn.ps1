$mvn = Get-ChildItem 'D:\' -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'apache-maven*' }
$mvn | ForEach-Object { Write-Host "Found: $($_.FullName)" }

$mvnCmd = Get-ChildItem 'D:\' -Recurse -Filter 'mvn.cmd' -ErrorAction SilentlyContinue -Depth 4 | Select-Object -First 5
$mvnCmd | ForEach-Object { Write-Host "mvn.cmd: $($_.FullName)" }

# Also check user profile
$mvnInUser = Get-ChildItem 'C:\Users\Administrator' -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'apache-maven*' -or $_.Name -like '.m2' }
$mvnInUser | ForEach-Object { Write-Host "User dir: $($_.FullName)" }

# Check Downloads
$dl = Get-ChildItem 'C:\Users\Administrator\Downloads' -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'apache-maven*' }
$dl | ForEach-Object { Write-Host "Download: $($_.FullName)" }
