Get-ChildItem 'd:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\*\target\*-1.0.0.jar' | ForEach-Object {
    [PSCustomObject]@{
        Module = $_.Directory.Parent.Name
        Name = $_.Name
        SizeMB = [math]::Round($_.Length/1MB, 1)
    }
} | Format-Table -AutoSize
