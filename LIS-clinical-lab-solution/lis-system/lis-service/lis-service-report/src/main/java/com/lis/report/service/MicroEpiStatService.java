package com.lis.report.service;

import com.lis.api.vo.MicroEpiStatVO;

/**
 * 微生物流行病学统计服务
 */
public interface MicroEpiStatService {

    /**
     * 汇总统计
     *
     * @param beginDate 开始日期 (yyyy-MM-dd), 可空
     * @param endDate   结束日期 (yyyy-MM-dd), 可空
     * @return 统计结果
     */
    MicroEpiStatVO statistics(String beginDate, String endDate);
}
