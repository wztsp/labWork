package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.LabDocument;

/**
 * 文档资料服务
 */
public interface LabDocumentService extends IService<LabDocument> {

    Page<LabDocument> queryPage(long current, long size, String keyword, String docType, Long groupId);

    LabDocument create(LabDocument document);

    LabDocument update(LabDocument document);

    /**
     * 作废文档
     */
    void abolish(Long id);

    void delete(Long id);
}
