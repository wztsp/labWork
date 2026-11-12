@echo off
chcp 65001 > nul
setlocal
set CP=D:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-qc\target\classes
set CP=%CP%;D:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-qc\target\test-classes
set CP=%CP%;D:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-common\target\classes
set CP=%CP%;D:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-core\target\classes
set CP=%CP%;D:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-base\target\classes
REM append all jars from local maven repo used. Use wildcard only in lib dirs.
for /f "delims=" %%f in ('dir /s /b "%USERPROFILE%\.m2\repository\org\springframework\spring-web\*.jar" 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "CP=!CP!;%%f"
for /f "delims=" %%f in ('dir /s /b "%USERPROFILE%\.m2\repository\org\springframework\spring-context\*.jar" 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "CP=!CP!;%%f"
for /f "delims=" %%f in ('dir /s /b "%USERPROFILE%\.m2\repository\org\springframework\spring-beans\*.jar" 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "CP=!CP!;%%f"
for /f "delims=" %%f in ('dir /s /b "%USERPROFILE%\.m2\repository\org\springframework\spring-core\*.jar" 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "CP=!CP!;%%f"
for /f "delims=" %%f in ('dir /s /b "%USERPROFILE%\.m2\repository\com\baomidou\mybatis-plus-core\*.jar" 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "CP=!CP!;%%f"
for /f "delims=" %%f in ('dir /s /b "%USERPROFILE%\.m2\repository\org\projectlombok\lombok\*.jar" 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "CP=!CP!;%%f"
for /f "delims=" %%f in ('dir /s /b "%USERPROFILE%\.m2\repository\com\alibaba\fastjson2\*.jar" 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "CP=!CP!;%%f"
cd /d D:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-qc\target\classes
"C:\Program Files\BellSoft\LibericaJDK-17\bin\javac.exe" -encoding UTF-8 -cp "%CP%" -d . D:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-qc\src\main\java\com\lis\qc\controller\OccController.java
echo ERRORLEVEL=%ERRORLEVEL%
