package com.lis.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.integration.entity.HisInterfaceLog;
import com.lis.integration.mapper.HisInterfaceLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * HIS 接口调用日志：落库 + 分页查询。
 * <p>
 * 日志写入失败不能影响接口主流程，统一吞掉异常只打 warn。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HisInterfaceLogService {

    /** 报文正文最大保留长度 */
    private static final int MAX_BODY = 8000;
    private static final int MAX_ERROR = 1000;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HisInterfaceLogMapper logMapper;

    public void record(String serviceNo, String actionNo, int direction, String url,
                       String requestBody, String responseBody, Integer httpStatus,
                       boolean success, String errorMsg, Long costMs, String refNo) {
        try {
            HisInterfaceLog record = new HisInterfaceLog();
            record.setServiceNo(serviceNo);
            record.setActionNo(actionNo);
            record.setDirection(direction);
            record.setUrl(truncate(url, 500));
            record.setRequestBody(truncate(requestBody, MAX_BODY));
            record.setResponseBody(truncate(responseBody, MAX_BODY));
            record.setHttpStatus(httpStatus);
            record.setSuccess(success ? 1 : 0);
            record.setErrorMsg(truncate(errorMsg, MAX_ERROR));
            record.setCostMs(costMs);
            record.setRefNo(truncate(refNo, 200));
            record.setCreateTime(LocalDateTime.now());
            logMapper.insert(record);
        } catch (Exception e) {
            log.warn("[HIS接口日志] 写入失败: {}", e.getMessage());
        }
    }

    public PageResult<HisInterfaceLog> queryPage(long current, long size, String serviceNo, Integer direction,
                                                 Integer success, String keyword,
                                                 String beginTime, String endTime) {
        LambdaQueryWrapper<HisInterfaceLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(!blank(serviceNo), HisInterfaceLog::getServiceNo, serviceNo)
                .eq(direction != null, HisInterfaceLog::getDirection, direction)
                .eq(success != null, HisInterfaceLog::getSuccess, success);
        if (!blank(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(HisInterfaceLog::getRefNo, value)
                    .or().like(HisInterfaceLog::getErrorMsg, value)
                    .or().like(HisInterfaceLog::getActionNo, value));
        }
        LocalDateTime begin = parseBegin(beginTime);
        LocalDateTime end = parseEnd(endTime);
        wrapper.ge(begin != null, HisInterfaceLog::getCreateTime, begin)
                .lt(end != null, HisInterfaceLog::getCreateTime, end)
                .orderByDesc(HisInterfaceLog::getId);
        Page<HisInterfaceLog> page = logMapper.selectPage(new Page<>(current, size), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss */
    private LocalDateTime parseBegin(String text) {
        if (blank(text)) {
            return null;
        }
        String value = text.trim().replace('T', ' ');
        return value.length() <= 10
                ? LocalDate.parse(value).atStartOfDay()
                : LocalDateTime.parse(value, TS);
    }

    /** 日期型结束时间按"当天 23:59:59"处理，即次日零点前 */
    private LocalDateTime parseEnd(String text) {
        if (blank(text)) {
            return null;
        }
        String value = text.trim().replace('T', ' ');
        return value.length() <= 10
                ? LocalDate.parse(value).plusDays(1).atStartOfDay()
                : LocalDateTime.parse(value, TS).plusSeconds(1);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "...(截断)" : text;
    }

    private boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
