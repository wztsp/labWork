@echo off
chcp 65001 > nul
cd /d D:\traeitem\labWork
"C:\Program Files\BellSoft\LibericaJDK-17\bin\javac.exe" -encoding UTF-8 -cp "C:\Users\Administrator\.m2\repository\org\postgresql\postgresql\42.6.2\postgresql-42.6.2.jar" _FixOccSchema.java
"C:\Program Files\BellSoft\LibericaJDK-17\bin\java.exe" -Dfile.encoding=UTF-8 -cp ".;C:\Users\Administrator\.m2\repository\org\postgresql\postgresql\42.6.2\postgresql-42.6.2.jar" _FixOccSchema
