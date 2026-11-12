package com.lis.instrument.collector;

import com.lis.instrument.entity.Instrument;

/**
 * 仪器结果采集器 (策略模式)
 * <p>
 * 用于支持非实时网络通讯的仪器:
 * <ul>
 *     <li>{@code FILE} - 仪器将结果写入本地文件, 采集器轮询读取并解析</li>
 *     <li>{@code DB}   - 仪器将结果写入其数据库, 采集器轮询查询并读取</li>
 * </ul>
 * 采集到的结构化结果统一交给 {@code ResultMatchingService.matchAndSave} 匹配落库。
 */
public interface ResultCollector {

    /**
     * 支持的连接方式: FILE / DB
     */
    String getConnectType();

    /**
     * 执行一次采集
     *
     * @param instrument 启用中的仪器 (connectType 与本采集器一致)
     */
    void collect(Instrument instrument);
}