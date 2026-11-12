package com.lis.reagent.support;

import com.lis.common.core.exception.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一业务单号生成器（零 Spring 依赖，可脱离容器直接单测）。
 *
 * <p><b>规则</b>：{@code 前缀 + yyyyMMdd + 4 位序号}，例如 {@code PUR202608040001}。
 * 前缀由调用方指定：PUR 采购订单 / REQ 请领（统一业务单）/ INB 入库单 / PA 采购申请。</p>
 *
 * <p><b>防撞策略</b>：基于库内当日同前缀的 MAX 单号续号，而不是依赖
 * {@code System.currentTimeMillis()} 或 UUID —— 后者在分布式环境下仍可能碰撞且号段不可读。
 * 续号思路参考 DDL 22.4 的 {@code SUP_MIG_XXXX} 续号（{@code MAX(...) + ROW_NUMBER()}）。</p>
 *
 * <p><b>并发说明</b>：单号表均有唯一索引（如 {@code uk_lis_reagent_purchase_order_no}），
 * 极端并发下即使两个请求取到同一个 MAX 序号，后提交的一方会撞唯一约束导致整个事务回滚
 * （MyBatis-Plus 会抛 {@code DuplicateKeyException}），由上层重试或报错兜底 ——
 * 数据库唯一约束是最后防线，本生成器只负责尽量降低碰撞概率。</p>
 *
 * <p><b>纯函数约定</b>：本类不做任何数据库读写，库内 MAX 查询通过调用方注入的
 * {@link #nextNo(String, LocalDate, Function)} 回调完成，便于脱离容器单测。</p>
 *
 * @author LIS Team
 */
public final class BizNoGenerator {

    /** 日期部分格式：yyyyMMdd */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 序号长度：4 位 */
    private static final int SEQ_LENGTH = 4;

    /** 最大序号 9999，当日用尽即抛异常（拒绝自动进位到 5 位，保持号长稳定） */
    private static final int MAX_SEQ = 9999;

    /** 单号整体正则：{@code 前缀+yyyyMMdd+序号}，用于从库内 MAX 值中提取序号 */
    private static final Pattern SEQ_PATTERN = Pattern.compile("^(.*?)(\\d{4,})$");

    private BizNoGenerator() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 生成下一个业务单号（默认取今天日期）。
     *
     * @param prefix        单号前缀（如 PUR / REQ / INB / PA），不可为 null 或空
     * @param maxNoFetcher  库内当前最大单号查询回调：入参为「前缀 + yyyyMMdd」的完整头部
     *                      （如 {@code PUR20260804}），返回库内匹配的最大单号或 null
     * @return 新单号，恒不为 null
     * @throws BusinessException 前缀为空 / 当日单号用尽时抛出
     */
    public static String nextNo(String prefix, Function<String, String> maxNoFetcher) {
        return nextNo(prefix, LocalDate.now(), maxNoFetcher);
    }

    /**
     * 生成下一个业务单号（指定日期，便于测试固定日期）。
     *
     * @param prefix        单号前缀（如 PUR / REQ / INB / PA），不可为 null 或空
     * @param date          单号日期，null 时取今天
     * @param maxNoFetcher  库内当前最大单号查询回调，不可为 null
     * @return 新单号，恒不为 null
     * @throws BusinessException 前缀为空 / maxNoFetcher 为 null / 当日单号用尽时抛出
     */
    public static String nextNo(String prefix, LocalDate date, Function<String, String> maxNoFetcher) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new BusinessException("单号前缀不能为空");
        }
        if (maxNoFetcher == null) {
            throw new BusinessException("单号最大值查询回调不能为空");
        }
        String datePart = (date == null ? LocalDate.now() : date).format(DATE_FORMAT);
        String head = prefix + datePart;
        String maxNo = maxNoFetcher.apply(head);
        int seq = parseSeq(maxNo, head);
        int next = seq + 1;
        if (next > MAX_SEQ) {
            throw new BusinessException(prefix + " 当日单号已用尽（超过 9999），请次日再试");
        }
        return head + String.format("%0" + SEQ_LENGTH + "d", next);
    }

    /**
     * 从库内最大单号中解析当日序号。
     *
     * @param maxNo 库内最大单号，可为 null（当天尚无单据）
     * @param head  前缀 + yyyyMMdd 头部
     * @return 已存在的最大序号；无匹配或单号非法时返回 0
     */
    public static int parseSeq(String maxNo, String head) {
        if (maxNo == null || head == null) {
            return 0;
        }
        String body = maxNo.trim();
        if (!body.startsWith(head)) {
            return 0;
        }
        String tail = body.substring(head.length());
        if (tail.isEmpty() || !tail.chars().allMatch(Character::isDigit)) {
            return 0;
        }
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从任意带尾部序号字符串中提取尾部数字序列（供续号思路复用）。
     *
     * @param raw 原始字符串，可为 null
     * @return 尾部数字序列，无匹配返回 null
     */
    public static String extractTrailingDigits(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = SEQ_PATTERN.matcher(raw.trim());
        return m.matches() ? m.group(2) : null;
    }
}
