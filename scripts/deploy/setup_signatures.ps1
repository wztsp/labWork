$env:PGPASSWORD = 'lis_dev_2026'
$psql = 'D:\Program Files\PostgreSQL\13\bin\psql.exe'

# 1. 将 signature_image 列扩容为 TEXT, 以容纳 base64 数据 URI
& $psql -h 127.0.0.1 -U lis_user -d lis_db -c "ALTER TABLE lis_staff ALTER COLUMN signature_image TYPE TEXT;"
& $psql -h 127.0.0.1 -U lis_user -d lis_db -c "SELECT column_name, data_type, character_maximum_length FROM information_schema.columns WHERE table_name='lis_staff' AND column_name='signature_image';"

# 2. 读取签名图片并转 base64 数据 URI
function Get-DataUri($path, $mime) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $b64 = [System.Convert]::ToBase64String($bytes)
    return "data:$mime;base64,$b64"
}

$zhang = Get-DataUri 'd:\traeitem\labWork\sig_zhang.jpg' 'image/jpeg'
$li    = Get-DataUri 'd:\traeitem\labWork\sig_li.jpg'    'image/jpeg'

# 3. 生成 SQL 文件
$sql = @"
UPDATE lis_staff SET signature_image = '$zhang' WHERE id = 2;
UPDATE lis_staff SET signature_image = '$li'    WHERE id = 3;
"@
$sqlPath = 'd:\traeitem\labWork\signature_update.sql'
[System.IO.File]::WriteAllText($sqlPath, $sql)

# 4. 执行
& $psql -h 127.0.0.1 -U lis_user -d lis_db -f $sqlPath
& $psql -h 127.0.0.1 -U lis_user -d lis_db -c "SELECT id, name, left(signature_image, 40) AS sig_prefix, length(signature_image) AS sig_len FROM lis_staff WHERE id IN (2,3);"