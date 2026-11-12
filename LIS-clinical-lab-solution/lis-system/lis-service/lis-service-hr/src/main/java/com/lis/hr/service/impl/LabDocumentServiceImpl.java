package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.LabDocument;
import com.lis.hr.mapper.LabDocumentMapper;
import com.lis.hr.service.LabDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LabDocumentServiceImpl extends ServiceImpl<LabDocumentMapper, LabDocument> implements LabDocumentService {

    @Override
    public Page<LabDocument> queryPage(long current, long size, String keyword, String docType, Long groupId) {
        LambdaQueryWrapper<LabDocument> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(LabDocument::getDocCode, keyword)
                    .or().like(LabDocument::getDocName, keyword));
        }
        if (docType != null && !docType.isEmpty()) {
            wrapper.eq(LabDocument::getDocType, docType);
        }
        if (groupId != null) {
            wrapper.eq(LabDocument::getGroupId, groupId);
        }
        wrapper.orderByDesc(LabDocument::getUploadTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LabDocument create(LabDocument document) {
        if (document.getDocCode() == null || document.getDocName() == null) {
            throw new BusinessException("文档编码和名称不能为空");
        }
        if (document.getVersion() == null) {
            document.setVersion("1.0");
        }
        if (document.getStatus() == null) {
            document.setStatus(1);
        }
        document.setUploadTime(LocalDateTime.now());
        this.save(document);
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LabDocument update(LabDocument document) {
        if (document.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(document);
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void abolish(Long id) {
        LabDocument doc = this.getById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在: " + id);
        }
        doc.setStatus(0);
        this.updateById(doc);
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }
}
