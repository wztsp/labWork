$services = Get-ChildItem 'HKLM:\SYSTEM\CurrentControlSet\Services' -ErrorAction SilentlyContinue
foreach ($svc in $services) {
    $name = $svc.PSChildName
    if ($name -like '*postgres*' -or $name -like '*pg_*') {
        Write-Host "Service: $name"
        $props = Get-ItemProperty $svc.PSPath -ErrorAction SilentlyContinue
        if ($props.ImagePath) { Write-Host "  Path: $($props.ImagePath)" }
    }
}

# Also try WMI with all users
Get-CimInstance Win32_Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*postgres*' -or $_.Name -like '*pg_*' } | ForEach-Object {
    Write-Host "Service: $($_.Name) | State: $($_.State) | Path: $($_.PathName)"
}
