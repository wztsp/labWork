package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.BloodCultureSubculture;

import java.util.List;

/**
 * 血培养转种记录服务
 */
public interface BloodCultureSubcultureService extends IService<BloodCultureSubculture> {

    List<BloodCultureSubculture> listByBloodCultureId(Long bloodCultureId);

    BloodCultureSubculture addSubculture(BloodCultureSubculture subculture);
}
