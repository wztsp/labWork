package com.lis.report.service;

import com.lis.report.entity.MicroAlert;

import java.util.List;

/**
 * 微生物特殊耐药/危急值预警服务
 */
public interface MicroAlertService {

    /** 查询全部预警 */
    List<MicroAlert> listAll(String status, String alertLevel);

    /** 根据报告ID查询预警 */
    List<MicroAlert> listByReportId(Long microReportId);

    /** 创建预警 */
    MicroAlert create(MicroAlert alert);

    /** 标记已读 */
    void markRead(Long id);

    /** 处理预警 */
    void handle(Long id, String handler, String remark);
}
