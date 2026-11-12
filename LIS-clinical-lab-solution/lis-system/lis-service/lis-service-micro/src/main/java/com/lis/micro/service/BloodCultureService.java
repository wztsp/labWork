package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.BloodCulture;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 血培养服务
 */
public interface BloodCultureService extends IService<BloodCulture> {

    Page<BloodCulture> queryPage(long current, long size, Long sampleId, Integer status);

    BloodCulture getBySampleId(Long sampleId);

    /**
     * 接种血培养瓶
     */
    BloodCulture inoculate(BloodCulture bloodCulture);

    /**
     * 接收阳性报警
     */
    BloodCulture receiveAlarm(Long id, LocalDateTime alarmTime, Long operatorId);

    /**
     * 报告最终结果
     */
    BloodCulture reportFinal(Long id, String finalResult, Long operatorId);

    /**
     * 查询培养中的血培养(用于看板)
     */
    List<BloodCulture> listCulturing();
}
