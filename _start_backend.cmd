@echo off
chcp 65001 > nul
set MAVEN_OPTS=-Dfile.encoding=UTF-8
cd /d D:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-monolith
C:\maven\apache-maven-3.9.9\bin\mvn.cmd "-Dfile.encoding=UTF-8" spring-boot:run -q
