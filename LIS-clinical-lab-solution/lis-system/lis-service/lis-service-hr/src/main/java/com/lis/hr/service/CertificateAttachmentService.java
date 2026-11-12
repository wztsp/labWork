package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.CertificateAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 资质证书附件服务
 */
public interface CertificateAttachmentService extends IService<CertificateAttachment> {

    /**
     * 上传附件
     */
    CertificateAttachment upload(Long certificateId, MultipartFile file, Long uploaderId, String uploaderName, String remark);

    /**
     * 下载附件
     */
    CertificateAttachment download(Long attachmentId);

    /**
     * 查询附件列表(不含文件数据)
     */
    List<CertificateAttachment> listByCertificateId(Long certificateId);

    /**
     * 获取附件数量
     */
    int countByCertificateId(Long certificateId);

    /**
     * 删除附件
     */
    void delete(Long attachmentId);

    /**
     * 获取附件(含文件数据)
     */
    CertificateAttachment getByIdWithData(Long attachmentId);
}
