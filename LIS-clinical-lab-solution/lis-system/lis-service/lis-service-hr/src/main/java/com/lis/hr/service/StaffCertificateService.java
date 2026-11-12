package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.StaffCertificate;

import java.util.List;

/**
 * 资质证书服务
 */
public interface StaffCertificateService extends IService<StaffCertificate> {

    List<StaffCertificate> listByStaffId(Long staffId);

    /**
     * 查询即将到期的证书
     */
    List<StaffCertificate> listExpiring(int withinDays);

    StaffCertificate create(StaffCertificate certificate);

    StaffCertificate update(StaffCertificate certificate);

    void delete(Long id);
}
