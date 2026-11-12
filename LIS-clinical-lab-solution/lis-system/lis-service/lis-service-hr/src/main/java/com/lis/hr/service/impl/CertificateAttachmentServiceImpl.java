package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.CertificateAttachment;
import com.lis.hr.entity.StaffCertificate;
import com.lis.hr.mapper.CertificateAttachmentMapper;
import com.lis.hr.mapper.StaffCertificateMapper;
import com.lis.hr.service.CertificateAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateAttachmentServiceImpl extends ServiceImpl<CertificateAttachmentMapper, CertificateAttachment>
        implements CertificateAttachmentService {

    private final StaffCertificateMapper certificateMapper;

    /**
     * 允许上传的文件类型
     */
    private static final String[] ALLOWED_CONTENT_TYPES = {
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/jpg",
            "image/gif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    };

    /**
     * 允许上传的文件扩展名
     */
    private static final String[] ALLOWED_EXTENSIONS = {
            ".pdf", ".jpg", ".jpeg", ".png", ".gif",
            ".doc", ".docx", ".xls", ".xlsx",
            ".PDF", ".JPG", ".JPEG", ".PNG", ".GIF",
            ".DOC", ".DOCX", ".XLS", ".XLSX"
    };

    /**
     * 最大文件大小 (10MB)
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateAttachment upload(Long certificateId, MultipartFile file, Long uploaderId, String uploaderName, String remark) {
        if (certificateId == null) {
            throw new BusinessException("证书ID不能为空");
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }

        // 验证文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过10MB");
        }

        // 验证文件扩展名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isAllowedExtension(originalFilename)) {
            throw new BusinessException("不支持的文件类型，仅支持 PDF、图片、Word、Excel 文档");
        }

        // 验证证书是否存在
        StaffCertificate certificate = certificateMapper.selectById(certificateId);
        if (certificate == null) {
            throw new BusinessException("证书不存在");
        }

        try {
            // 获取文件数据
            byte[] fileData = file.getBytes();

            // 获取文件扩展名
            String fileType = getFileExtension(originalFilename);

            // 创建附件记录
            CertificateAttachment attachment = new CertificateAttachment();
            attachment.setCertificateId(certificateId);
            attachment.setFileName(originalFilename);
            attachment.setFileType(fileType);
            attachment.setFileSize(file.getSize());
            attachment.setContentType(file.getContentType());
            attachment.setFileData(fileData);
            attachment.setUploaderId(uploaderId);
            attachment.setUploaderName(uploaderName);
            attachment.setUploadTime(LocalDateTime.now());
            attachment.setRemark(remark);

            this.save(attachment);

            // 更新证书的附件数量
            int count = baseMapper.countByCertificateId(certificateId);
            certificate.setAttachmentCount(count);
            certificateMapper.updateById(certificate);

            log.info("附件上传成功: certificateId={}, fileName={}, fileSize={}", certificateId, originalFilename, file.getSize());

            return attachment;

        } catch (Exception e) {
            log.error("附件上传失败: {}", e.getMessage(), e);
            throw new BusinessException("附件上传失败: " + e.getMessage());
        }
    }

    @Override
    public CertificateAttachment download(Long attachmentId) {
        CertificateAttachment attachment = this.getById(attachmentId);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        return attachment;
    }

    @Override
    public List<CertificateAttachment> listByCertificateId(Long certificateId) {
        return baseMapper.listByCertificateId(certificateId);
    }

    @Override
    public int countByCertificateId(Long certificateId) {
        return baseMapper.countByCertificateId(certificateId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long attachmentId) {
        CertificateAttachment attachment = this.getById(attachmentId);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }

        Long certificateId = attachment.getCertificateId();

        this.removeById(attachmentId);

        // 更新证书的附件数量
        int count = baseMapper.countByCertificateId(certificateId);
        StaffCertificate certificate = certificateMapper.selectById(certificateId);
        if (certificate != null) {
            certificate.setAttachmentCount(count);
            certificateMapper.updateById(certificate);
        }

        log.info("附件删除成功: attachmentId={}, certificateId={}", attachmentId, certificateId);
    }

    @Override
    public CertificateAttachment getByIdWithData(Long attachmentId) {
        CertificateAttachment attachment = this.getById(attachmentId);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        return attachment;
    }

    private boolean isAllowedExtension(String filename) {
        for (String ext : ALLOWED_EXTENSIONS) {
            if (filename.toLowerCase().endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1).toLowerCase();
    }
}
