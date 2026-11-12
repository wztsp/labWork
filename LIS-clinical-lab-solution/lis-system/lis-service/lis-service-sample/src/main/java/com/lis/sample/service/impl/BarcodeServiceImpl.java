package com.lis.sample.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.util.StringUtil;
import com.lis.common.redis.RedisUtil;
import com.lis.sample.entity.BarcodeRule;
import com.lis.sample.entity.Sample;
import com.lis.sample.mapper.BarcodeRuleMapper;
import com.lis.sample.mapper.SampleDataMapper;
import com.lis.sample.service.BarcodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条码 Service 实现
 * 基于可配置规则生成条码, 支持多种格式:
 * - 纯数字 (如 0000000001)
 * - 年月日+数字 (如 202607310001)
 * - 2位年+月日+数字 (如 2607310001)
 * - 字母+数字 (如 S0000000001)
 * - 字母+年月日+数字 (如 S202607310001)
 *
 * 序列号策略:
 * - daily: 每日重置 (Redis按天key, fallback数据库查询当天最大序号)
 * - global: 全局递增 (Redis全局key, fallback数据库查询全局最大序号)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodeServiceImpl implements BarcodeService {

    private final RedisUtil redisUtil;
    private final SampleDataMapper sampleMapper;
    private final BarcodeRuleMapper barcodeRuleMapper;
    private final JdbcTemplate jdbcTemplate;

    /** Redis 序列号 Key 前缀 */
    private static final String SEQ_KEY_PREFIX = "lis:barcode:seq:";

    /**
     * Redis 不可用时的进程内兜底计数器。
     * key = seqKey(ruleCode 或 ruleCode+日期), 首次使用以数据库当前最大值预热,
     * 之后在内存中原子自增, 保证同进程内一次性批量/并发生成的多条条码序号不重复,
     * 且与历史已提交数据不冲突。
     */
    private final ConcurrentHashMap<String, AtomicLong> seqCounters = new ConcurrentHashMap<>();

    /** 默认规则编码 */
    private static final String DEFAULT_RULE_CODE = "SAMPLE";

    @Override
    public String generateBarcode() {
        return generateBarcode(DEFAULT_RULE_CODE);
    }

    @Override
    public String generateBarcode(String ruleCode) {
        BarcodeRule rule = getEnabledRule(ruleCode);
        if (rule == null) {
            throw new BusinessException("未找到启用的条码规则: " + ruleCode);
        }
        // 1. 拼接前缀
        String prefix = rule.getPrefix() != null ? rule.getPrefix() : "";
        // 2. 拼接日期
        String datePart = formatDatePart(rule.getDatePattern());
        // 3. 生成序列号
        long seq = nextSeq(rule, datePart);
        int seqLength = rule.getSeqLength() != null ? rule.getSeqLength() : 6;
        String seqStr = String.format("%0" + seqLength + "d", seq);
        // 4. 拼接完整条码
        String barcode = prefix + datePart + seqStr;
        log.debug("生成条码: ruleCode={}, barcode={}, seq={}", ruleCode, barcode, seq);
        return barcode;
    }

    @Override
    public boolean validateBarcode(String barcode) {
        if (StringUtil.isEmpty(barcode)) {
            return false;
        }
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sample::getSampleBarcode, barcode);
        Long count = sampleMapper.selectCount(wrapper);
        return count == null || count == 0;
    }

    @Override
    public List<BarcodeRule> listRules() {
        LambdaQueryWrapper<BarcodeRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BarcodeRule::getId);
        return barcodeRuleMapper.selectList(wrapper);
    }

    @Override
    public BarcodeRule getEnabledRule(String ruleCode) {
        LambdaQueryWrapper<BarcodeRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BarcodeRule::getRuleCode, ruleCode)
               .eq(BarcodeRule::getEnabled, 1)
               .last("LIMIT 1");
        return barcodeRuleMapper.selectOne(wrapper);
    }

    @Override
    public void updateRule(BarcodeRule rule) {
        if (rule.getId() == null) {
            throw new BusinessException("规则ID不能为空");
        }
        BarcodeRule existing = barcodeRuleMapper.selectById(rule.getId());
        if (existing == null) {
            throw new BusinessException("条码规则不存在: id=" + rule.getId());
        }
        // 校验 seqLength 范围
        if (rule.getSeqLength() != null && (rule.getSeqLength() < 1 || rule.getSeqLength() > 10)) {
            throw new BusinessException("序列号长度必须在1-10之间");
        }
        // 校验 datePattern
        if (rule.getDatePattern() != null) {
            validateDatePattern(rule.getDatePattern());
        }
        // 校验 seqStrategy
        if (rule.getSeqStrategy() != null
                && !"daily".equals(rule.getSeqStrategy())
                && !"global".equals(rule.getSeqStrategy())) {
            throw new BusinessException("序列号策略必须是 daily 或 global");
        }
        LambdaUpdateWrapper<BarcodeRule> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(BarcodeRule::getId, rule.getId())
                .set(rule.getRuleName() != null, BarcodeRule::getRuleName, rule.getRuleName())
                .set(rule.getPrefix() != null, BarcodeRule::getPrefix, rule.getPrefix())
                .set(rule.getDatePattern() != null, BarcodeRule::getDatePattern, rule.getDatePattern())
                .set(rule.getSeqLength() != null, BarcodeRule::getSeqLength, rule.getSeqLength())
                .set(rule.getSeqStrategy() != null, BarcodeRule::getSeqStrategy, rule.getSeqStrategy())
                .set(rule.getRemark() != null, BarcodeRule::getRemark, rule.getRemark())
                .set(BarcodeRule::getUpdateTime, LocalDateTime.now());
        barcodeRuleMapper.update(null, updateWrapper);
        log.info("条码规则更新成功: id={}, ruleCode={}", rule.getId(), existing.getRuleCode());
    }

    /**
     * 格式化日期部分
     */
    private String formatDatePart(String datePattern) {
        if (StringUtil.isEmpty(datePattern) || "none".equals(datePattern)) {
            return "";
        }
        LocalDate today = LocalDate.now();
        switch (datePattern) {
            case "yyyyMMdd":
                return today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            case "yyMMdd":
                return today.format(DateTimeFormatter.ofPattern("yyMMdd"));
            case "yyyyMM":
                return today.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "yyyy":
                return today.format(DateTimeFormatter.ofPattern("yyyy"));
            default:
                throw new BusinessException("不支持的日期格式: " + datePattern);
        }
    }

    /**
     * 校验日期格式
     */
    private void validateDatePattern(String datePattern) {
        if (!"none".equals(datePattern) && !"yyyyMMdd".equals(datePattern)
                && !"yyMMdd".equals(datePattern) && !"yyyyMM".equals(datePattern)
                && !"yyyy".equals(datePattern)) {
            throw new BusinessException("日期格式必须是: none/yyyyMMdd/yyMMdd/yyyyMM/yyyy");
        }
    }

    /**
     * 获取下一个序列号
     * 优先使用 Redis incr, Redis 不可用时 fallback 到数据库查询最大序号
     */
    private long nextSeq(BarcodeRule rule, String datePart) {
        String strategy = rule.getSeqStrategy() != null ? rule.getSeqStrategy() : "daily";
        String seqKey;
        if ("daily".equals(strategy)) {
            // 每日重置: key含日期
            String dateKey = datePart.isEmpty()
                    ? LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    : datePart;
            seqKey = SEQ_KEY_PREFIX + rule.getRuleCode() + ":" + dateKey;
        } else {
            // 全局递增: key不含日期
            seqKey = SEQ_KEY_PREFIX + rule.getRuleCode();
        }
        // 尝试 Redis
        if (redisUtil.isRedisAvailable()) {
            try {
                // 先查询数据库中的最大值
                long dbMax = maxSeqFromDb(rule, datePart);
                // 获取 Redis 中当前的值
                Long redisVal = redisUtil.get(seqKey, Long.class);
                
                // 决定使用的起始值: 取 Redis 和数据库中的较大值
                long startVal;
                if (redisVal == null || redisVal < dbMax) {
                    // Redis 没有值或值小于数据库最大值，需要同步
                    startVal = dbMax;
                    log.info("同步Redis序列号: ruleCode={}, dbMax={}, redisVal={}", rule.getRuleCode(), dbMax, redisVal);
                    // 将 Redis 设置为数据库最大值
                    redisUtil.set(seqKey, dbMax);
                } else {
                    startVal = redisVal;
                }
                
                // 使用 incr 获取下一个值 (incr 会在当前值基础上 +1)
                Long seq = redisUtil.incr(seqKey);
                if (seq != null && seq > 0) {
                    return seq;
                }
            } catch (Exception e) {
                log.warn("Redis不可用, fallback到数据库查询序列号: {}", e.getMessage());
            }
        } else {
            log.debug("Redis未启用, 直接使用数据库序列号 (ruleCode={})", rule.getRuleCode());
        }
        // Fallback: 使用进程内原子计数器(以数据库当前最大序号预热)。
        return nextSeqAtomic(seqKey, rule, datePart);
    }

    /**
     * 进程内原子序号: 确保计数器 >= 数据库已提交的最大序号后自增。
     * 唯一的全局单调递增由 AtomicLong 保证, 不受事务可见性影响。
     */
    private long nextSeqAtomic(String seqKey, BarcodeRule rule, String datePart) {
        AtomicLong counter = seqCounters.computeIfAbsent(seqKey, k -> new AtomicLong(0));
        long dbMax = maxSeqFromDb(rule, datePart);
        // 将计数器下限抬到数据库最大值, 避免与历史/其他进程已提交数据冲突
        counter.accumulateAndGet(dbMax, Math::max);
        return counter.incrementAndGet();
    }

    /**
     * 从数据库查询当前已提交的最大序列号(未匹配到返回 0)。
     * 根据规则(ruleCode)查询对应的业务表, 精确匹配前缀+日期+seqLength位数字, 提取序列号部分。
     * 例如: SAMPLE->lis_sample.sample_barcode, ORDER->lis_lab_order.order_no,
     *       VISIT->lis_visit.visit_no, PATIENT->lis_patient.patient_no
     */
    private long maxSeqFromDb(BarcodeRule rule, String datePart) {
        String prefix = rule.getPrefix() != null ? rule.getPrefix() : "";
        String strategy = rule.getSeqStrategy() != null ? rule.getSeqStrategy() : "daily";
        int seqLength = rule.getSeqLength() != null ? rule.getSeqLength() : 6;

        // 依据规则编码定位目标表与序列号列
        String table;
        String column;
        switch (rule.getRuleCode() == null ? "" : rule.getRuleCode().toUpperCase()) {
            case "ORDER":
                table = "lis_lab_order"; column = "order_no"; break;
            case "VISIT":
                table = "lis_visit"; column = "visit_no"; break;
            case "PATIENT":
                table = "lis_patient"; column = "patient_no"; break;
            default:
                table = "lis_sample"; column = "sample_barcode"; break;
        }

        // 构造精确匹配的正则模式 (PostgreSQL 使用 POSIX 正则)
        String exactPrefix;
        if ("daily".equals(strategy)) {
            exactPrefix = prefix + (datePart == null ? "" : datePart);
        } else {
            exactPrefix = prefix;
        }
        
        // 在 SQL 中使用正则表达式精确匹配，并提取序列号进行数值排序
        // 格式: 前缀 + [日期] + 恰好 seqLength 位数字
        // PostgreSQL 使用 POSIX 正则: [0-9] 匹配数字, {n} 匹配恰好 n 次
        String pgRegexPattern = "'" + exactPrefix + "[0-9]{" + seqLength + "}$'";
        
        // 使用 PostgreSQL 的正则匹配，只选符合精确格式的记录
        // 并在 ORDER BY 中按序列号数值排序（提取后缀转为数字）
        String sql;
        if (exactPrefix.isEmpty()) {
            // 无前缀情况 (如 SAMPLE 规则)
            sql = "SELECT " + column + " FROM " + table + 
                  " WHERE " + column + " ~ " + pgRegexPattern +
                  " ORDER BY CAST(substring(" + column + " FROM " + seqLength + ") AS BIGINT) DESC" +
                  " LIMIT 1";
        } else {
            // 有前缀情况 - 提取前缀后的部分进行排序
            int suffixStartPos = exactPrefix.length() + 1; // PostgreSQL substring 从 1 开始
            sql = "SELECT " + column + " FROM " + table + 
                  " WHERE " + column + " ~ " + pgRegexPattern +
                  " ORDER BY CAST(substring(" + column + " FROM " + suffixStartPos + ") AS BIGINT) DESC" +
                  " LIMIT 1";
        }
        
        List<String> candidates;
        try {
            log.info("执行SQL查询: {}", sql);
            candidates = jdbcTemplate.queryForList(sql, String.class);
            log.info("SQL查询结果数量: {}", candidates != null ? candidates.size() : 0);
            if (candidates != null && !candidates.isEmpty()) {
                log.info("最大条码: {}", candidates.get(0));
            }
        } catch (Exception e) {
            log.warn("查询数据库最大序列号失败(正则匹配), 尝试回退到简单查询: {}", e.getMessage());
            // 回退方案: 使用 LIKE 查询所有记录，然后在内存中过滤
            String fallbackSql = "SELECT " + column + " FROM " + table + " WHERE " + column + " LIKE ? LIMIT 5000";
            String likePrefix = exactPrefix + "%";
            log.info("执行回退SQL查询: {}, 参数: {}", fallbackSql, likePrefix);
            try {
                candidates = jdbcTemplate.queryForList(fallbackSql, String.class, likePrefix);
                log.info("回退SQL查询结果数量: {}", candidates != null ? candidates.size() : 0);
            } catch (Exception e2) {
                log.warn("回退查询也失败, 从0开始: {}", e2.getMessage());
                return 0L;
            }
            
            // 在内存中过滤并找到最大序列号
            if (candidates == null || candidates.isEmpty()) {
                return 0L;
            }
            
            long maxSeq = 0L;
            for (String barcode : candidates) {
                if (barcode == null) continue;
                
                String suffix;
                if (!prefix.isEmpty() && barcode.startsWith(prefix)) {
                    suffix = barcode.substring(prefix.length());
                } else {
                    suffix = barcode;
                }
                
                if ("daily".equals(strategy) && datePart != null && !datePart.isEmpty() && suffix.startsWith(datePart)) {
                    suffix = suffix.substring(datePart.length());
                }
                
                if (suffix.length() == seqLength && suffix.matches("\\d+")) {
                    try {
                        long seq = Long.parseLong(suffix);
                        if (seq > maxSeq) {
                            maxSeq = seq;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            if (maxSeq == 0L) {
                log.debug("未找到符合格式的条码(回退模式), {}从0开始", rule.getRuleCode());
                return 0L;
            }
            
            log.debug("查询到最大序列号(回退模式): ruleCode={}, maxSeq={}", rule.getRuleCode(), maxSeq);
            try {
                redisUtil.set(SEQ_KEY_PREFIX + rule.getRuleCode()
                        + ("daily".equals(strategy) ? (":" + datePart) : ""), maxSeq);
            } catch (Exception ignored) {}
            return maxSeq;
        }

        if (candidates == null || candidates.isEmpty()) {
            log.debug("未找到符合格式的条码(正则匹配), {}从0开始", rule.getRuleCode());
            return 0L;
        }

        // 从查询结果提取序列号
        String maxBarcode = candidates.get(0);
        if (maxBarcode == null) {
            return 0L;
        }
        
        // 解析序列号 (直接用 SQL 中已排序好的结果)
        String suffix;
        if (!prefix.isEmpty() && maxBarcode.startsWith(prefix)) {
            suffix = maxBarcode.substring(prefix.length());
        } else {
            suffix = maxBarcode;
        }
        
        if ("daily".equals(strategy) && datePart != null && !datePart.isEmpty() && suffix.startsWith(datePart)) {
            suffix = suffix.substring(datePart.length());
        }
        
        long maxSeq = 0L;
        if (suffix.length() == seqLength && suffix.matches("\\d+")) {
            try {
                maxSeq = Long.parseLong(suffix);
            } catch (NumberFormatException ignored) {}
        }
        
        if (maxSeq == 0L) {
            log.debug("序列号解析失败, {}从0开始, barcode={}", rule.getRuleCode(), maxBarcode);
            return 0L;
        }
        
        log.debug("查询到最大序列号: ruleCode={}, maxSeq={}, barcode={}", rule.getRuleCode(), maxSeq, maxBarcode);
        
        // 将最大值写入Redis
        try {
            redisUtil.set(SEQ_KEY_PREFIX + rule.getRuleCode()
                    + ("daily".equals(strategy) ? (":" + datePart) : ""), maxSeq);
        } catch (Exception ignored) {}
        
        return maxSeq;
    }
}
