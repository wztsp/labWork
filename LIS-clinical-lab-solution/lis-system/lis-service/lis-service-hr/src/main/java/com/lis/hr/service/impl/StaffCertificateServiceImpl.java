package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.StaffCertificate;
import com.lis.hr.mapper.StaffCertificateMapper;
import com.lis.hr.service.StaffCertificateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaffCertificateServiceImpl extends ServiceImpl<StaffCertificateMapper, StaffCertificate>
        implements StaffCertificateService {

    @Override
    public List<StaffCertificate> listByStaffId(Long staffId) {
        return this.list(new LambdaQueryWrapper<StaffCertificate>()
                .eq(StaffCertificate::getStaffId, staffId)
                .orderByDesc(StaffCertificate::getExpiryDate));
    }

    @Override
    public List<StaffCertificate> listExpiring(int withinDays) {
        LocalDate now = LocalDate.now();
        LocalDate deadline = now.plusDays(withinDays);
        return this.list(new LambdaQueryWrapper<StaffCertificate>()
                .eq(StaffCertificate::getStatus, 1)
                .between(StaffCertificate::getExpiryDate, now, deadline));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffCertificate create(StaffCertificate certificate) {
        if (certificate.getStaffId() == null) {
            throw new BusinessException("人员ID不能为空");
        }
        if (certificate.getCertType() == null || certificate.getCertName() == null) {
            throw new BusinessException("证书类型和名称不能为空");
        }
        if (certificate.getStatus() == null) {
            certificate.setStatus(1);
        }
        if (certificate.getAlertDays() == null) {
            certificate.setAlertDays(30);
        }
        certificate.setCreateTime(LocalDateTime.now());
        this.save(certificate);
        return certificate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffCertificate update(StaffCertificate certificate) {
        if (certificate.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(certificate);
        return certificate;
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }
}
