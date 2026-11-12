package com.lis.instrument.collector;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.instrument.collector.db.DbDialect;
import com.lis.instrument.collector.support.ResultConstants;
import com.lis.instrument.entity.Instrument;
import com.lis.instrument.entity.InstrumentCommLog;
import com.lis.instrument.mapper.InstrumentCommMapper;
import com.lis.instrument.matching.ResultMatchingService;
import com.lis.instrument.service.CommLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库采集器 (connectType=DB)
 * <p>
 * 通过 JDBC 连接仪器结果库, 按配置的列映射读取结果表, 交给
 * {@code ResultMatchingService.matchAndSave}。采用<b>水位线</b>增量读取:
 * 记录上次成功读取时间 {@code last_polled_time}, 仅读取水位线列晚于该时间的记录, 避免重复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbResultCollector implements ResultCollector {

    private final InstrumentCommMapper instrumentMapper;
    private final ResultMatchingService resultMatchingService;
    private final CommLogService commLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getConnectType() {
        return "DB";
    }

    @Override
    public void collect(Instrument instrument) {
        if (instrument.getDbTable() == null || instrument.getDbTable().isBlank()) {
            log.warn("仪器 [{}] 未配置 db_table, 跳过数据库采集", instrument.getId());
            return;
        }
        String parseStatus = "SUCCESS";
        String parseResult = null;
        try {
            List<Map<String, Object>> rows = queryRows(instrument);
            List<Map<String, String>> results = toResults(instrument, rows);
            parseResult = objectMapper.writeValueAsString(results);
            if (!results.isEmpty()) {
                resultMatchingService.matchAndSave(instrument.getId(), instrument.getProtocolType(), results);
            }
            // 更新水位线
            updateWatermark(instrument.getId());
            log.info("仪器 [{}] 数据库采集完成, 读取 {} 行, 有效结果 {} 条",
                    instrument.getId(), rows.size(), results.size());
        } catch (Exception e) {
            parseStatus = "FAIL";
            parseResult = "{\"error\":\"" + e.getMessage() + "\"}";
            log.error("仪器 [{}] 数据库采集失败", instrument.getId(), e);
        } finally {
            saveCommLog(instrument, parseStatus, parseResult);
        }
    }

    /** 执行增量查询 */
    private List<Map<String, Object>> queryRows(Instrument instrument) throws Exception {
        String driver = DbDialect.resolveDriver(instrument.getDbType(), instrument.getDbDriver());
        String url = DbDialect.buildUrl(instrument.getDbType(), instrument.getDbHost(),
                instrument.getDbPort(), instrument.getDbName());
        StringBuilder sql = new StringBuilder("SELECT ");
        LinkedHashMap<String, String> mapping = DbDialect.parseColumnMapping(instrument.getDbColumns());
        if (mapping.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", mapping.values().stream().distinct().toList()));
        }
        sql.append(" FROM ").append(instrument.getDbTable());
        List<Object> params = new ArrayList<>();

        List<String> conds = new ArrayList<>();
        if (instrument.getDbWhere() != null && !instrument.getDbWhere().isBlank()) {
            conds.add(instrument.getDbWhere());
        }
        // 水位线过滤
        if (instrument.getDbWatermarkCol() != null && !instrument.getDbWatermarkCol().isBlank()
                && instrument.getLastPolledTime() != null) {
            conds.add(instrument.getDbWatermarkCol() + " > ?");
            params.add(java.sql.Timestamp.valueOf(instrument.getLastPolledTime()));
        }
        if (!conds.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conds));
        }

        try (Connection conn = DbDialect.open(url, instrument.getDbUser(), instrument.getDbPassword(), driver);
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return DbDialect.readRows(rs);
            }
        }
    }

    /** 将读取到的行按列映射转换为标准结果字段 */
    private List<Map<String, String>> toResults(Instrument instrument, List<Map<String, Object>> rows) {
        LinkedHashMap<String, String> mapping = DbDialect.parseColumnMapping(instrument.getDbColumns());
        List<Map<String, String>> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> r = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : mapping.entrySet()) {
                String field = e.getKey();
                String srcCol = e.getValue();
                Object raw = row.get(srcCol);
                if (raw == null) {
                    raw = row.get(srcCol.toLowerCase());
                }
                String text = DbDialect.toText(raw);
                if (text != null && !text.isEmpty()) {
                    // 兼容: 源列 exam_no 等作为样本号
                    r.put(field, text);
                }
            }
            // 仅保留有样本号+检验项目的结果, 否则跳过
            String sampleNo = r.get(ResultConstants.SAMPLE_NO);
            String code = r.get(ResultConstants.TEST_CODE);
            if ((sampleNo == null || sampleNo.isEmpty()) && r.get(ResultConstants.SEQ) == null) {
                continue;
            }
            if (code == null || code.isEmpty()) {
                continue;
            }
            results.add(r);
        }
        return results;
    }

    private void updateWatermark(Long instrumentId) {
        try {
            instrumentMapper.update(null, new LambdaUpdateWrapper<Instrument>()
                    .eq(Instrument::getId, instrumentId)
                    .set(Instrument::getLastPolledTime, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("仪器 [{}] 更新水位线失败", instrumentId, e);
        }
    }

    private void saveCommLog(Instrument instrument, String parseStatus, String parseResult) {
        try {
            InstrumentCommLog commLog = new InstrumentCommLog();
            commLog.setInstrumentId(instrument.getId());
            commLog.setDirection("RECEIVE");
            commLog.setProtocolType(instrument.getProtocolType());
            commLog.setRawMessage("DB: " + instrument.getDbTable());
            commLog.setParseStatus(parseStatus);
            commLog.setParseResult(parseResult);
            commLog.setMessageTime(LocalDateTime.now());
            commLogService.saveLog(commLog);
        } catch (Exception e) {
            log.error("仪器 [{}] 保存数据库采集通讯日志失败", instrument.getId(), e);
        }
    }
}