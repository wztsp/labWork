package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.StaffTraining;

import java.util.List;

/**
 * 培训记录服务
 */
public interface StaffTrainingService extends IService<StaffTraining> {

    List<StaffTraining> listByStaffId(Long staffId);

    StaffTraining create(StaffTraining training);

    StaffTraining update(StaffTraining training);

    void delete(Long id);
}
