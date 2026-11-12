package com.lis.monolith.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.monolith.entity.Message;
import com.lis.monolith.mapper.MessageMapper;
import com.lis.monolith.mapper.TatDataMapper;
import com.lis.monolith.service.MessageService;
import com.lis.monolith.vo.InfectiousScanRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 传染病消息定时任务
 * 定期扫描已审核结果中命中传染病配置且"阳性"的结果, 生成传染病消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfectiousMessageTask {

    private final TatDataMapper tatDataMapper;
    private final MessageMapper messageMapper;
    private final MessageService messageService;

    @Scheduled(fixedDelayString = "${infectious.scan.interval-ms:600000}", initialDelay = 60000)
    public void scan() {
        try {
            // 只处理最近 1 小时的新结果
            List<InfectiousScanRow> rows = tatDataMapper.selectInfectiousRows(LocalDateTime.now().minusHours(1));
            if (rows == null || rows.isEmpty()) {
                return;
            }
            // 已通知过的 resultId 集合
            Set<Long> notifiedResult = new HashSet<>();
            for (InfectiousScanRow row : rows) {
                if (!isPositive(row)) {
                    continue;
                }
                Long count = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                        .eq(Message::getMessageType, Message.TYPE_INFECTIOUS)
                        .eq(Message::getBusinessId, row.getResultId()));
                if (count != null && count > 0) {
                    notifiedResult.add(row.getResultId());
                    continue;
                }
                String title = "传染病阳性提醒";
                String content = String.format("患者 %s 标本 %s 检验项目 %s 结果 %s 为阳性, 请及时登记上报",
                        row.getPatientName(), row.getSampleBarcode(),
                        row.getTestItemName(), row.getResultText() != null ? row.getResultText() : row.getResultValue());
                messageService.broadcast(Message.TYPE_INFECTIOUS, title, content,
                        row.getResultId(), row.getSampleId(), row.getTestItemId());
                notifiedResult.add(row.getResultId());
            }
            log.info("传染病扫描完成: 命中 {} 条, 新增通知 resultId 数 {}", rows.size(), notifiedResult.size());
        } catch (Exception e) {
            log.error("传染病扫描失败", e);
        }
    }

    private boolean isPositive(InfectiousScanRow row) {
        String pos = row.getPositiveText() != null ? row.getPositiveText() : "阳性";
        if (row.getResultText() != null && row.getResultText().contains(pos)) {
            return true;
        }
        if (row.getResultValue() != null && row.getResultValue().contains(pos)) {
            return true;
        }
        return false;
    }
}