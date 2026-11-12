package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.StaffTitle;

import java.util.List;

/**
 * 人员职称服务
 */
public interface StaffTitleService extends IService<StaffTitle> {

    List<StaffTitle> listByStaffId(Long staffId);

    /**
     * 获取当前职称
     */
    StaffTitle getCurrentTitle(Long staffId);

    StaffTitle create(StaffTitle staffTitle);

    StaffTitle update(StaffTitle staffTitle);

    void delete(Long id);
}
