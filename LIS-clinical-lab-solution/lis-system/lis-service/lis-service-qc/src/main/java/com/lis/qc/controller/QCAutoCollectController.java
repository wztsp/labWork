package com.lis.qc.controller;

import com.lis.api.dto.QCRecordDTO;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.R;
import com.lis.qc.dto.AutoCollectDTO;
import com.lis.qc.service.QCRecordService;
import com.lis.qc.service.rule.QCResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 日常质控仪器自动采集 Controller
 * <p>
 * 接收仪器推送的质控数据, 保存为 QCRecord 并触发 Westgard 判定。
 */
@Slf4j
@RestController
@RequestMapping("/api/qc/auto-collect")
@RequiredArgsConstructor
public class QCAutoCollectController {

    private final QCRecordService qcRecordService;

    /**
     * 接收仪器推送的质控数据
     */
    @PostMapping
    public R<QCResult> receive(@RequestBody AutoCollectDTO dto) {
        if (dto.getLotId() == null) {
            throw new BusinessException("质控品批号ID(lotId)不能为空");
        }
        if (dto.getValue() == null) {
            throw new BusinessException("质控测量值(value)不能为空");
        }
        QCRecordDTO recordDTO = QCRecordDTO.builder()
                .qcLotId(dto.getLotId())
                .instrumentId(dto.getInstrumentId())
                .measureValue(dto.getValue())
                .measureTime(dto.getTestTime())
                .operatorId(dto.getOperatorId())
                .build();
        QCResult result = qcRecordService.submitRecord(recordDTO);
        log.info("仪器自动采集质控数据: lotId={}, value={}, result={}",
                dto.getLotId(), dto.getValue(), result.getLevel());
        return R.ok(result);
    }
}
