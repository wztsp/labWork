package com.lis.api.service;

import com.lis.api.dto.ResultDTO;

import java.util.List;

/**
 * 检验结果持久化端口（进程内直连）。
 * <p>
 * 由报告服务实现，供仪器模块在 RabbitMQ 不可用时直接落库，
 * 保证模拟仪器/实体仪器上传结果能端到端生效，不依赖 MQ 基础设施。
 */
public interface ResultPersistencePort {

    /**
     * 持久化仪器上传的检验结果（内部会解析 itemCode -> testItemId 并落库）
     *
     * @param dtos 解析后的结果列表
     * @return 落库的 resultId 列表
     */
    List<Long> persistResults(List<ResultDTO> dtos);
}