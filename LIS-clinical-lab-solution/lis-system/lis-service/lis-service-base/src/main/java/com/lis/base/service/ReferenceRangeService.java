package com.lis.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.base.entity.ReferenceRange;

import java.math.BigDecimal;
import java.util.List;

/**
 * 参考范围 Service
 */
public interface ReferenceRangeService extends IService<ReferenceRange> {

    /**
     * 分页查询参考范围
     */
    Page<ReferenceRange> queryPage(long current, long size, Long testItemId, String specimenType, Long instrumentId);

    /**
     * 新增
     */
    ReferenceRange create(ReferenceRange referenceRange);

    /**
     * 更新
     */
    ReferenceRange update(ReferenceRange referenceRange);

    /**
     * 删除
     */
    void delete(Long id);

    /**
     * 按项目ID查询参考范围
     */
    List<ReferenceRange> listByItemId(Long testItemId);

    /**
     * 按项目+性别+年龄查找匹配的参考范围
     *
     * @param testItemId 项目ID
     * @param gender     性别 0通用 1男 2女
     * @param age        年龄
     */
    ReferenceRange findMatch(Long testItemId, Integer gender, BigDecimal age);

    /**
     * 按项目+性别+年龄+生理条件查找匹配的参考范围
     * 优先匹配 conditionRemark 完全相同的记录, 若无则回退到 conditionRemark 为空的通用记录
     *
     * @param testItemId      项目ID
     * @param gender          性别 0通用 1男 2女
     * @param age             年龄
     * @param conditionRemark 生理条件: 如 女性-卵泡期/立位/晨8点/空腹/孕妇-孕早期 等
     */
    ReferenceRange findMatch(Long testItemId, Integer gender, BigDecimal age, String conditionRemark);
}
