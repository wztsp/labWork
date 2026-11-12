package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.Staff;

import java.util.List;

/**
 * 人员档案服务
 */
public interface StaffService extends IService<Staff> {

    Page<Staff> queryPage(long current, long size, String keyword, Long groupId, Integer status);

    Staff getByStaffNo(String staffNo);

    List<Staff> listByGroupId(Long groupId);

    Staff create(Staff staff);

    Staff update(Staff staff);

    /**
     * 离职处理：账号停用，记录离职日期
     */
    void resign(Long id);

    void delete(Long id);

    /**
     * 即将到期证书预警
     */
    List<Staff> listStaffWithExpiringCertificates(int withinDays);
}
