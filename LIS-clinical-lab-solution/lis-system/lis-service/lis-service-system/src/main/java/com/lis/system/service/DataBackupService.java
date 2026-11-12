package com.lis.system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 数据库备份服务
 * <p>
 * 通过 pg_dump 命令备份 PostgreSQL 数据库。
 * - 支持手动触发全量备份
 * - 每日凌晨 2 点自动 (增量简化为全量) 备份
 * - 每周日凌晨 3 点全量备份
 * - 备份文件保留 backup.retention-days 天, 超期自动清理
 */
@Slf4j
@Service
public class DataBackupService {

    @Value("${backup.dir:./backups}")
    private String backupDir;

    @Value("${backup.retention-days:90}")
    private int retentionDays;

    @Value("${backup.pg-dump-path:pg_dump}")
    private String pgDumpPath;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${backup.db-name:lis_db}")
    private String dbName;

    @Value("${backup.db-host:localhost}")
    private String dbHost;

    @Value("${backup.db-port:5432}")
    private String dbPort;

    @PostConstruct
    public void init() {
        try {
            Path dir = Paths.get(backupDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("[DataBackup] 创建备份目录: {}", dir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("[DataBackup] 初始化备份目录失败: {}", backupDir, e);
        }
    }

    /**
     * 手动触发全量备份
     */
    public Map<String, Object> manualBackup() {
        log.info("[DataBackup] 手动触发全量备份");
        return doBackup("manual");
    }

    /**
     * 每日凌晨 2 点自动备份 (简化为全量 pg_dump)
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${backup.daily-cron:0 0 2 * * ?}")
    public void dailyBackup() {
        log.info("[DataBackup] 每日定时备份开始");
        try {
            doBackup("daily");
        } catch (Exception e) {
            log.error("[DataBackup] 每日定时备份失败", e);
        }
    }

    /**
     * 每周日凌晨 3 点全量备份
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${backup.weekly-cron:0 0 3 ? * SUN}")
    public void weeklyBackup() {
        log.info("[DataBackup] 每周定时全量备份开始");
        try {
            doBackup("weekly");
        } catch (Exception e) {
            log.error("[DataBackup] 每周定时备份失败", e);
        }
    }

    /**
     * 清理过期备份 (保留 retentionDays 天)
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${backup.cleanup-cron:0 30 2 * * ?}")
    public void cleanupExpiredBackups() {
        try {
            cleanup();
        } catch (Exception e) {
            log.error("[DataBackup] 清理过期备份失败", e);
        }
    }

    private Map<String, Object> doBackup(String type) {
        Map<String, Object> result = new HashMap<>();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("%s_%s_%s.sql", dbName, type, timestamp);
        Path filePath = Paths.get(backupDir, fileName);

        ProcessBuilder pb = buildProcess(filePath);
        pb.redirectErrorStream(true);
        int exitCode = -1;
        try {
            Process process = pb.start();
            // 消费输出避免阻塞
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[DataBackup] pg_dump: {}", line);
                }
            }
            exitCode = process.waitFor();
        } catch (Exception e) {
            log.error("[DataBackup] 执行 pg_dump 失败", e);
        }

        result.put("fileName", fileName);
        result.put("type", type);
        result.put("backupTime", timestamp);
        File f = filePath.toFile();
        long size = f.exists() ? f.length() : 0L;
        result.put("size", size);
        result.put("success", exitCode == 0 && f.exists() && size > 0);
        if (exitCode != 0) {
            result.put("message", "pg_dump 退出码: " + exitCode + " (请确认 pg_dump 已安装且路径配置正确)");
        } else {
            result.put("message", "备份成功");
        }
        log.info("[DataBackup] 备份完成: {} success={} size={}", fileName, result.get("success"), size);
        return result;
    }

    private ProcessBuilder buildProcess(Path outputFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add(pgDumpPath);
        cmd.add("-h"); cmd.add(dbHost);
        cmd.add("-p"); cmd.add(dbPort);
        cmd.add("-U"); cmd.add(dbUser);
        cmd.add("-F"); cmd.add("p");
        cmd.add("-f"); cmd.add(outputFile.toAbsolutePath().toString());
        cmd.add(dbName);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", dbPassword == null ? "" : dbPassword);
        pb.directory(new File(backupDir));
        return pb;
    }

    /**
     * 列出全部备份文件
     */
    public List<Map<String, Object>> listBackups() {
        Path dir = Paths.get(backupDir);
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sql")
                            || p.getFileName().toString().endsWith(".sql.gz")
                            || p.getFileName().toString().endsWith(".dump"))
                    .map(this::toBackupInfo)
                    .sorted(Comparator.comparing(m -> (String) m.get("fileName"), Comparator.reverseOrder()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("[DataBackup] 列出备份文件失败", e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> toBackupInfo(Path p) {
        Map<String, Object> info = new HashMap<>();
        File f = p.toFile();
        info.put("fileName", f.getName());
        info.put("size", f.length());
        info.put("lastModified", LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(f.lastModified()),
                java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return info;
    }

    /**
     * 获取备份文件资源 (下载用)
     */
    public Resource getBackupResource(String fileName) {
        if (!isSafeFileName(fileName)) {
            throw new IllegalArgumentException("非法文件名: " + fileName);
        }
        Path filePath = Paths.get(backupDir, fileName).normalize();
        Path base = Paths.get(backupDir).normalize();
        if (!filePath.startsWith(base)) {
            throw new IllegalArgumentException("非法路径访问: " + fileName);
        }
        File f = filePath.toFile();
        if (!f.exists() || !f.isFile()) {
            throw new IllegalArgumentException("备份文件不存在: " + fileName);
        }
        return new FileSystemResource(f);
    }

    /**
     * 删除备份文件
     */
    public boolean deleteBackup(String fileName) {
        if (!isSafeFileName(fileName)) {
            throw new IllegalArgumentException("非法文件名: " + fileName);
        }
        Path filePath = Paths.get(backupDir, fileName).normalize();
        Path base = Paths.get(backupDir).normalize();
        if (!filePath.startsWith(base)) {
            throw new IllegalArgumentException("非法路径访问: " + fileName);
        }
        try {
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("[DataBackup] 删除备份文件失败: {}", fileName, e);
            return false;
        }
    }

    private void cleanup() {
        Path dir = Paths.get(backupDir);
        if (!Files.exists(dir)) {
            return;
        }
        LocalDate deadline = LocalDate.now().minusDays(retentionDays);
        int deleted = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path p : files) {
                try {
                    File f = p.toFile();
                    LocalDate modified = LocalDate.ofInstant(
                            java.time.Instant.ofEpochMilli(f.lastModified()),
                            java.time.ZoneId.systemDefault());
                    if (modified.isBefore(deadline)) {
                        Files.deleteIfExists(p);
                        deleted++;
                    }
                } catch (Exception ignore) {
                    // ignore single file failure
                }
            }
        } catch (IOException e) {
            log.error("[DataBackup] 清理过期备份时读取目录失败", e);
        }
        if (deleted > 0) {
            log.info("[DataBackup] 清理过期备份完成: 删除 {} 个文件 (保留 {} 天)", deleted, retentionDays);
        }
    }

    private boolean isSafeFileName(String fileName) {
        return fileName != null && !fileName.isEmpty()
                && !fileName.contains("..") && !fileName.contains("/") && !fileName.contains("\\")
                && !fileName.contains(":");
    }
}
