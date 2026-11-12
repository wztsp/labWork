$env:JAVA_HOME = 'C:\Program Files\BellSoft\LibericaJDK-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$mvn = 'C:\Users\Administrator\AppData\Local\maven\apache-maven-3.9.9\bin\mvn.cmd'
$proj = 'd:\traeitem\labWork\LIS-clinical-lab-solution\lis-system'
& $mvn -f $proj clean package -DskipTests -T 1C 2>&1 | Select-Object -Last 60
