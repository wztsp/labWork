package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.StaffAuthorization;

import java.util.List;

/**
 * 岗位授权服务
 */
public interface StaffAuthorizationService extends IService<StaffAuthorization> {

    List<StaffAuthorization> listByStaffId(Long staffId);

    /**
     * 校验员工是否拥有指定授权
     */
    boolean hasAuthorization(Long staffId, String authType, String scope);

    StaffAuthorization create(StaffAuthorization authorization);

    StaffAuthorization update(StaffAuthorization authorization);

    void delete(Long id);
}
