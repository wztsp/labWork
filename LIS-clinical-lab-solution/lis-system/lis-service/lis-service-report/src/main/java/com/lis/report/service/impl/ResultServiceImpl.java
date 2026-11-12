package com.lis.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.api.dto.ResultDTO;
import com.lis.api.vo.ResultVO;
import com.lis.report.entity.Result;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.service.CriticalValueService;
import com.lis.report.service.ResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 检验结果服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultServiceImpl implements ResultService {

    private final ResultMapper resultMapper;
    private final CriticalValueService criticalValueService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${mq.exchange:lis.exchange}")
    private String exchange;
    @Value("${mq.report.result-saved-routing-key:result.saved}")
    private String resultSavedRoutingKey;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveResult(ResultDTO dto) {
        // 幂等: 同一标本 + 同一检验指标 已存在结果时做 UPDATE，保证 MQ 重试/双写机制 不产生重复行
        Result existing = null;
        if (dto.getSampleId() != null && dto.getTestItemId() != null) {
            existing = resultMapper.selectOne(
                    new LambdaQueryWrapper<Result>()
                            .eq(Result::getSampleId, dto.getSampleId())
                            .eq(Result::getTestItemId, dto.getTestItemId())
                            .last("LIMIT 1"));
        }
        Result r;
        boolean isUpdate;
        if (existing != null) {
            r = existing;
            isUpdate = true;
            // 只更新回传结果相关的字段（保留人工录入/审核的状态）
            if (dto.getResultValue() != null) r.setResultValue(dto.getResultValue());
            if (dto.getResultText() != null) r.setResultText(dto.getResultText());
            if (dto.getUnit() != null) r.setUnit(dto.getUnit());
            if (dto.getRefRange() != null) r.setRefRange(dto.getRefRange());
            if (dto.getAbnormalFlag() != null) r.setAbnormalFlag(dto.getAbnormalFlag());
            if (dto.getTestTime() != null) r.setTestTime(dto.getTestTime());
            if (dto.getInstrumentId() != null && r.getInstrumentId() == null) r.setInstrumentId(dto.getInstrumentId());
            r.setUpdateTime(LocalDateTime.now());
        } else {
            r = new Result();
            BeanUtils.copyProperties(dto, r);
            isUpdate = false;
            r.setAuditStatus(Result.AUDIT_NONE);
            r.setCreateTime(LocalDateTime.now());
            r.setUpdateTime(LocalDateTime.now());
        }
        if (r.getIsCritical() == null) {
            r.setIsCritical(0);
        }
        // 危急值自动识别: 查询该项目参考范围的危急值上下限, 超出则标记危急值
        if (r.getTestItemId() != null && r.getResultValue() != null) {
            try {
                double value = Double.parseDouble(r.getResultValue().trim());
                Map<String, Object> criticalRange = resultMapper.selectCriticalRange(r.getTestItemId());
                if (criticalRange != null) {
                    boolean isCritical = false;
                    Object lowObj = criticalRange.get("criticallow");
                    if (lowObj == null) lowObj = criticalRange.get("criticalLow");
                    Object highObj = criticalRange.get("criticalhigh");
                    if (highObj == null) highObj = criticalRange.get("criticalHigh");
                    if (lowObj != null) {
                        double criticalLow = Double.parseDouble(String.valueOf(lowObj));
                        if (value < criticalLow) isCritical = true;
                    }
                    if (highObj != null) {
                        double criticalHigh = Double.parseDouble(String.valueOf(highObj));
                        if (value > criticalHigh) isCritical = true;
                    }
                    if (isCritical) {
                        r.setIsCritical(1);
                        log.warn("检测到危急值: testItemId={}, value={}, criticalLow={}, criticalHigh={}",
                                r.getTestItemId(), value, lowObj, highObj);
                    }
                }
            } catch (NumberFormatException ignore) {
                // 非数字结果不参与危急值判定
            }
        }
        if (isUpdate) {
            resultMapper.updateById(r);
            log.info("更新检验结果: id={}, sampleId={}, item={}, value={}, isCritical={}",
                    r.getId(), r.getSampleId(), dto.getTestItemCode(), r.getResultValue(), r.getIsCritical());
        } else {
            resultMapper.insert(r);
            log.info("保存检验结果: id={}, sampleId={}, item={}, isCritical={}",
                    r.getId(), r.getSampleId(), dto.getTestItemCode(), r.getIsCritical());
        }

        // 危急值通知: 触发告警 (MQ不可用时不阻断主流程)
        if (r.getIsCritical() != null && r.getIsCritical() == 1) {
            try {
                criticalValueService.alertCritical(r.getId());
            } catch (Exception e) {
                log.warn("危急值通知发送失败(不阻断): resultId={}, err={}", r.getId(), e.getMessage());
            }
        }

        // 发送结果保存事件, 触发自动报告生成 (MQ不可用时不阻断主流程)
        if (r.getSampleId() != null) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("sampleId", r.getSampleId());
                payload.put("resultId", r.getId());
                rabbitTemplate.convertAndSend(exchange, resultSavedRoutingKey, payload);
            } catch (Exception e) {
                log.warn("result.saved 消息发送失败(不阻断): sampleId={}, err={}", r.getSampleId(), e.getMessage());
            }
        }
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchSaveResults(List<ResultDTO> dtos) {
        List<Long> ids = new ArrayList<>();
        for (ResultDTO dto : dtos) {
            ids.add(saveResult(dto));
        }
        return ids;
    }

    @Override
    public List<ResultVO> getResultsBySampleId(Long sampleId) {
        List<Result> results = resultMapper.selectList(
                new LambdaQueryWrapper<Result>().eq(Result::getSampleId, sampleId)
                        .orderByAsc(Result::getTestTime));
        if (results.isEmpty()) {
            return new ArrayList<>();
        }
        // 一次性查询所有检验指标对应的父级检验项目(检验项目)信息, 用于按检验项目分组显示
        List<Long> itemIds = results.stream()
                .map(Result::getTestItemId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Map<String, Object>> panelInfoMap = new HashMap<>();
        if (!itemIds.isEmpty()) {
            List<Map<String, Object>> panelInfoList = resultMapper.selectPanelInfoForItems(itemIds);
            for (Map<String, Object> row : panelInfoList) {
                Object tidObj = row.get("test_item_id");
                if (tidObj != null) {
                    Long tid = ((Number) tidObj).longValue();
                    panelInfoMap.put(tid, row);
                }
            }
        }

        // 历史结果对比: 查询该标本对应患者的上次同项目已发布结果
        Long patientId = resultMapper.selectPatientIdBySample(sampleId);

        return results.stream().map(r -> {
            ResultVO vo = new ResultVO();
            BeanUtils.copyProperties(r, vo);
            // 填充检验项目(检验项目)分组信息, 支持一个条码多个检验项目的分组显示
            Map<String, Object> info = panelInfoMap.get(r.getTestItemId());
            if (info != null) {
                Object parentId = info.get("parent_id");
                Object parentName = info.get("parent_name");
                Object itemCode = info.get("item_code");
                Object itemName = info.get("item_name");
                if (parentId != null) {
                    vo.setPanelId(((Number) parentId).longValue());
                }
                if (parentName != null) {
                    vo.setPanelName(String.valueOf(parentName));
                }
                // 如果结果本身没存 testItemCode/testItemName, 从字典补全
                if (vo.getTestItemCode() == null && itemCode != null) {
                    vo.setTestItemCode(String.valueOf(itemCode));
                }
                if (vo.getTestItemName() == null && itemName != null) {
                    vo.setTestItemName(String.valueOf(itemName));
                }
            }
            // 填充上次结果 (历史结果对比)
            if (patientId != null && r.getTestItemId() != null) {
                Result last = resultMapper.selectLastPublishedByPatientAndItem(
                        patientId, r.getTestItemId(), sampleId);
                if (last != null) {
                    vo.setLastResult(last.getResultValue());
                    vo.setLastFlag(last.getAbnormalFlag());
                    vo.setLastTestTime(last.getTestTime());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> trendAnalysis(Long patientId, Long testItemId) {
        // 查询该患者该项目最近20次结果 (mapper 返回按 test_time DESC)
        List<Result> results = resultMapper.selectRecentByPatientAndItem(patientId, testItemId);
        // 反转为时间升序, 便于前端按时间顺序绘制趋势曲线
        List<Map<String, Object>> series = new ArrayList<>();
        for (int i = results.size() - 1; i >= 0; i--) {
            Result r = results.get(i);
            Map<String, Object> point = new HashMap<>();
            point.put("resultId", r.getId());
            point.put("sampleId", r.getSampleId());
            point.put("testItemId", r.getTestItemId());
            point.put("resultValue", r.getResultValue());
            point.put("resultText", r.getResultText());
            point.put("unit", r.getUnit());
            point.put("abnormalFlag", r.getAbnormalFlag());
            point.put("isCritical", r.getIsCritical());
            point.put("testTime", r.getTestTime());
            series.add(point);
        }
        log.info("趋势分析: patientId={}, testItemId={}, 共 {} 个数据点", patientId, testItemId, series.size());
        return series;
    }
}
