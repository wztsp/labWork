package com.lis.instrument.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.instrument.entity.InstrumentCommLog;
import com.lis.instrument.matching.ResultMatchingService;
import com.lis.instrument.protocol.ProtocolParser;
import com.lis.instrument.protocol.ProtocolParserFactory;
import com.lis.instrument.service.CommLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 希森美康仪器处理器
 * 默认使用 ASTM 协议。
 */
@Slf4j
@Component("SysmexHandler")
@RequiredArgsConstructor
public class SysmexHandler implements InstrumentHandler {

    private final ProtocolParserFactory parserFactory;
    private final ResultMatchingService resultMatchingService;
    private final CommLogService commLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getBrand() {
        return "SYSMEX";
    }

    @Override
    public void handleData(String rawData, Long instrumentId) {
        log.info("希森美康仪器 [{}] 收到数据: {} bytes", instrumentId, rawData.length());
        String protocolType = "ASTM";
        String parseStatus = "SUCCESS";
        String parseResult = null;
        try {
            ProtocolParser parser = parserFactory.getParser(protocolType);
            List<Map<String, String>> results = parser.parse(rawData);
            parseResult = objectMapper.writeValueAsString(results);
            resultMatchingService.matchAndSave(instrumentId, protocolType, results);
        } catch (Exception e) {
            parseStatus = "FAIL";
            parseResult = "{\"error\":\"" + e.getMessage() + "\"}";
            log.error("希森美康仪器 [{}] 数据解析失败", instrumentId, e);
        } finally {
            saveCommLog(instrumentId, rawData, protocolType, parseStatus, parseResult);
        }
    }

    private void saveCommLog(Long instrumentId, String rawData, String protocolType,
                             String parseStatus, String parseResult) {
        InstrumentCommLog commLog = new InstrumentCommLog();
        commLog.setInstrumentId(instrumentId);
        commLog.setDirection("RECEIVE");
        commLog.setRawMessage(rawData);
        commLog.setProtocolType(protocolType);
        commLog.setParseStatus(parseStatus);
        commLog.setParseResult(parseResult);
        commLog.setMessageTime(LocalDateTime.now());
        commLogService.saveLog(commLog);
    }
}
