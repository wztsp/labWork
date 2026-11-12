package com.lis.hr.controller;

import com.lis.common.core.result.R;
import com.lis.hr.entity.CertificateAttachment;
import com.lis.hr.service.CertificateAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 资质证书附件 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/hr/certificates/attachments")
@RequiredArgsConstructor
public class CertificateAttachmentController {

    private final CertificateAttachmentService attachmentService;

    /**
     * 上传附件
     */
    @PostMapping("/upload")
    public R<CertificateAttachment> upload(
            @RequestParam("certificateId") Long certificateId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploaderId", required = false) Long uploaderId,
            @RequestParam(value = "uploaderName", required = false) String uploaderName,
            @RequestParam(value = "remark", required = false) String remark) {

        // 如果没有指定上传人，使用默认值
        if (uploaderId == null) {
            uploaderId = 1L;
            uploaderName = uploaderName != null ? uploaderName : "系统";
        }

        CertificateAttachment attachment = attachmentService.upload(
                certificateId, file, uploaderId, uploaderName, remark);

        // 清除文件数据，只返回元信息
        attachment.setFileData(null);

        return R.ok(attachment);
    }

    /**
     * 查询证书的附件列表
     */
    @GetMapping("/certificate/{certificateId}")
    public R<List<CertificateAttachment>> listByCertificateId(@PathVariable Long certificateId) {
        List<CertificateAttachment> attachments = attachmentService.listByCertificateId(certificateId);
        // 清除文件数据，只返回元信息
        attachments.forEach(a -> a.setFileData(null));
        return R.ok(attachments);
    }

    /**
     * 获取附件数量
     */
    @GetMapping("/count/{certificateId}")
    public R<Integer> countByCertificateId(@PathVariable Long certificateId) {
        return R.ok(attachmentService.countByCertificateId(certificateId));
    }

    /**
     * 下载附件
     */
    @GetMapping("/download/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId) {
        CertificateAttachment attachment = attachmentService.getByIdWithData(attachmentId);

        byte[] fileData = attachment.getFileData();
        ByteArrayResource resource = new ByteArrayResource(fileData);

        // 对文件名进行URL编码，支持中文文件名
        String encodedFileName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType() != null ?
                        attachment.getContentType() : "application/octet-stream"))
                .contentLength(fileData.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }

    /**
     * 删除附件
     */
    @DeleteMapping("/{attachmentId}")
    public R<Void> delete(@PathVariable Long attachmentId) {
        attachmentService.delete(attachmentId);
        return R.ok();
    }

    /**
     * 预览附件(返回文件数据用于前端预览)
     */
    @GetMapping("/preview/{attachmentId}")
    public ResponseEntity<Resource> preview(@PathVariable Long attachmentId) {
        CertificateAttachment attachment = attachmentService.getByIdWithData(attachmentId);

        byte[] fileData = attachment.getFileData();
        ByteArrayResource resource = new ByteArrayResource(fileData);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType() != null ?
                        attachment.getContentType() : "application/octet-stream"))
                .contentLength(fileData.length)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(resource);
    }
}
