package com.lis.reagent.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算实扣领域事件（预留扩展点，设计文档 §2.7）。
 *
 * <p>在预算实扣成功后发布（事务提交前后均可被 {@code @TransactionalEventListener}
 * 订阅者接收）。本轮<b>不写任何订阅者</b>，仅保留发布点，未来对接财务系统时
 * 只需新增订阅者，主流程一行不改。</p>
 *
 * @param budgetId   预算 ID
 * @param orderId    触发实扣的采购订单 ID
 * @param orderNo    采购订单号
 * @param amount     实扣金额
 * @param deptId     科室 ID
 * @param budgetYear 预算年度
 * @param occurredAt 发生时间
 * @author LIS Team
 */
public record BudgetSettledEvent(Long budgetId, Long orderId, String orderNo,
                                 BigDecimal amount, Long deptId, Integer budgetYear,
                                 LocalDateTime occurredAt) {
}
