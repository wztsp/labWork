package com.lis.system.controller;

import com.lis.common.core.result.R;
import com.lis.common.security.RequirePermission;
import com.lis.system.service.DataBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 数据库备份 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/system/backup")
@RequirePermission("system:user")
@RequiredArgsConstructor
public class DataBackupController {

    private final DataBackupService dataBackupService;

    /**
     * 手动触发备份
     */
    @PostMapping("/manual")
    public R<Map<String, Object>> manualBackup() {
        return R.ok(dataBackupService.manualBackup());
    }

    /**
     * 备份文件列表
     */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(dataBackupService.listBackups());
    }

    /**
     * 下载备份文件
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) {
        Resource resource = dataBackupService.getBackupResource(filename);
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 删除备份文件
     */
    @DeleteMapping("/{filename}")
    public R<Boolean> delete(@PathVariable String filename) {
        return R.ok(dataBackupService.deleteBackup(filename));
    }
}
