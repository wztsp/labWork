package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.micro.entity.CulturePlan;
import com.lis.micro.entity.CulturePlanItem;
import com.lis.micro.entity.SamplePlate;
import com.lis.micro.mapper.CulturePlanItemMapper;
import com.lis.micro.mapper.CulturePlanMapper;
import com.lis.micro.mapper.SamplePlateMapper;
import com.lis.micro.service.SamplePlateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 标本培养皿条码服务实现
 */
@Service
public class SamplePlateServiceImpl extends ServiceImpl<SamplePlateMapper, SamplePlate> implements SamplePlateService {

    private final CulturePlanMapper planMapper;
    private final CulturePlanItemMapper planItemMapper;

    private static final DateTimeFormatter BARCODE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public SamplePlateServiceImpl(CulturePlanMapper planMapper, CulturePlanItemMapper planItemMapper) {
        this.planMapper = planMapper;
        this.planItemMapper = planItemMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SamplePlate> generatePlates(String sampleBarcode, Long sampleId,
                                            String sampleType, Long planId,
                                            List<Long> testItemIds, String patientName,
                                            Long operatorId, String operatorName) {
        if (sampleBarcode == null || sampleBarcode.isEmpty()) {
            throw new BusinessException("标本条码不能为空");
        }
        // 归一化标本类型 (去空白)
        String st = (sampleType == null) ? "" : sampleType.trim();

        // 查询匹配的启用培养方案: 优先按前端传的 planId, 其次按 检验项目+标本类型 匹配
        List<CulturePlan> plans;
        if (planId != null) {
            CulturePlan plan = planMapper.selectOne(new LambdaQueryWrapper<CulturePlan>()
                    .eq(CulturePlan::getId, planId)
                    .eq(CulturePlan::getStatus, 1));
            if (plan == null) {
                throw new BusinessException("所选培养方案不存在或已停用");
            }
            plans = java.util.Collections.singletonList(plan);
        } else {
            if (CollectionUtils.isEmpty(testItemIds)) {
                throw new BusinessException("标本未关联检验项目, 且未选中培养方案, 无法生成培养皿");
            }
            List<CulturePlan> available = planMapper.selectList(new LambdaQueryWrapper<CulturePlan>()
                    .in(CulturePlan::getTestItemId, testItemIds)
                    .eq(CulturePlan::getStatus, 1));
            // 按标本类型过滤: 空标本类型命中"全部"方案(plan.sampleType 为空); 非空标本类型命中 该类型专属 或 "全部" 方案
            List<CulturePlan> matched = new ArrayList<>();
            for (CulturePlan p : available) {
                String ps = p.getSampleType() == null ? "" : p.getSampleType().trim();
                if (st.isEmpty()) {
                    // 未选/无标本类型 -> 只匹配适用于全部标本类型的方案
                    if (ps.isEmpty()) matched.add(p);
                } else {
                    // 有标本类型 -> 匹配 该类型专属 或 全部
                    if (ps.isEmpty() || ps.equals(st)) matched.add(p);
                }
            }
            if (matched.isEmpty()) {
                throw new BusinessException(st.isEmpty()
                        ? "所选检验项目未配置培养方案, 请先在培养方案管理中配置"
                        : "所选检验项目未配置适用于标本类型【" + st + "】的培养方案, 请先在培养方案管理中配置");
            }
            // 排序: 默认方案优先; 若标本类型存在, 专属方案(非空sampleType)排在"全部"方案之前
            matched.sort((a, b) -> {
                int cmp = Integer.compare(b.getIsDefault() == null ? 0 : b.getIsDefault(),
                        a.getIsDefault() == null ? 0 : a.getIsDefault());
                if (cmp != 0) return cmp;
                int ba = (a.getSampleType() == null || a.getSampleType().trim().isEmpty()) ? 0 : 1;
                int bb = (b.getSampleType() == null || b.getSampleType().trim().isEmpty()) ? 0 : 1;
                return Integer.compare(bb, ba);
            });
            plans = matched;
        }

        // 重新生成前: 检查同一采集条码下是否存在已核收(status=1)的培养基条码
        // 如有, 必须先撤销核收(即删除关联微生物报告, 使 plate 状态回 0) 后才能重新生成
        List<SamplePlate> allPlatesForSample = this.list(new LambdaQueryWrapper<SamplePlate>()
                .eq(SamplePlate::getSampleBarcode, sampleBarcode));
        if (!CollectionUtils.isEmpty(allPlatesForSample)) {
            List<String> received = new ArrayList<>();
            for (SamplePlate p : allPlatesForSample) {
                int pst = p.getStatus() == null ? 0 : p.getStatus();
                if (pst == 1) received.add(p.getPlateBarcode());
            }
            if (!received.isEmpty()) {
                String tips = received.size() > 3
                        ? String.join("、", received.subList(0, 3)) + " 等" + received.size() + "条"
                        : String.join("、", received);
                throw new BusinessException("存在已核收的培养基条码(" + tips +
                        "), 请先撤销对应微生物报告(或取消所有培养基条码核收)后再重新生成");
            }
        }

        // 重新生成: 清理该标本下各 plan 中未核收或已作废的旧 plate, 避免多方案互相覆盖
        for (CulturePlan plan : plans) {
            this.remove(new LambdaQueryWrapper<SamplePlate>()
                    .eq(SamplePlate::getSampleBarcode, sampleBarcode)
                    .eq(SamplePlate::getPlanId, plan.getId())
                    .and(w -> w
                            .isNull(SamplePlate::getStatus)
                            .or()
                            .eq(SamplePlate::getStatus, 0)
                            .or()
                            .eq(SamplePlate::getStatus, 2)));
        }

        List<SamplePlate> result = new ArrayList<>();
        int seq = 1;
        for (CulturePlan plan : plans) {
            List<CulturePlanItem> items = planItemMapper.selectList(
                    new LambdaQueryWrapper<CulturePlanItem>()
                            .eq(CulturePlanItem::getPlanId, plan.getId())
                            .orderByAsc(CulturePlanItem::getSortOrder));
            if (CollectionUtils.isEmpty(items)) {
                continue;
            }
            for (CulturePlanItem item : items) {
                int qty = item.getQuantity() == null ? 1 : item.getQuantity();
                for (int i = 0; i < qty; i++) {
                    SamplePlate plate = new SamplePlate();
                    plate.setSampleBarcode(sampleBarcode);
                    plate.setSampleId(sampleId);
                    plate.setPlanId(plan.getId());
                    plate.setMediumTypeId(item.getMediumTypeId());
                    plate.setMediumTypeName(item.getMediumTypeName());
                    plate.setPatientName(patientName);
                    plate.setSeqNo(seq++);
                    plate.setStatus(0);
                    plate.setOperatorId(operatorId);
                    plate.setOperatorName(operatorName);
                    plate.setPlateBarcode(generatePlateBarcode());
                    plate.setCreateTime(LocalDateTime.now());
                    this.save(plate);
                    result.add(plate);
                }
            }
        }
        if (result.isEmpty()) {
            throw new BusinessException("培养方案未配置培养基, 无法生成培养皿");
        }
        return result;
    }

    /** 生成唯一培养皿条码: L + 时间戳 + 3位随机 */
    private String generatePlateBarcode() {
        while (true) {
            String barcode = "L" + LocalDateTime.now().format(BARCODE_TS)
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
            long count = this.count(new LambdaQueryWrapper<SamplePlate>()
                    .eq(SamplePlate::getPlateBarcode, barcode));
            if (count == 0) {
                return barcode;
            }
        }
    }

    @Override
    public List<SamplePlate> listBySampleId(Long sampleId) {
        return this.list(new LambdaQueryWrapper<SamplePlate>()
                .eq(SamplePlate::getSampleId, sampleId)
                .orderByAsc(SamplePlate::getSeqNo));
    }

    @Override
    public List<SamplePlate> listBySampleBarcode(String sampleBarcode) {
        return this.list(new LambdaQueryWrapper<SamplePlate>()
                .eq(SamplePlate::getSampleBarcode, sampleBarcode)
                .orderByAsc(SamplePlate::getSeqNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPlate(Long plateId) {
        if (plateId == null) throw new BusinessException("培养基ID不能为空");
        SamplePlate plate = this.getById(plateId);
        if (plate == null) throw new BusinessException("培养基记录不存在");
        // 状态: 0=未核收/生成中 NULL=未设置 1=已核收 2=已作废
        int st = plate.getStatus() == null ? 0 : plate.getStatus();
        if (st == 1) throw new BusinessException("该培养基条码已核收，请先撤销微生物报告后再取消");
        if (st == 2) return; // 已作废视为取消成功
        plate.setStatus(2); // 2=作废
        plate.setCancelTime(LocalDateTime.now());
        this.updateById(plate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAllBySample(Long sampleId, String sampleBarcode) {
        LambdaQueryWrapper<SamplePlate> q = new LambdaQueryWrapper<>();
        if (sampleId != null) {
            q.eq(SamplePlate::getSampleId, sampleId);
        } else if (sampleBarcode != null && !sampleBarcode.isEmpty()) {
            q.eq(SamplePlate::getSampleBarcode, sampleBarcode);
        } else {
            throw new BusinessException("标本ID或条码至少提供一项");
        }
        List<SamplePlate> list = this.list(q);
        if (list.isEmpty()) return;
        // 检查是否有已核收的
        for (SamplePlate p : list) {
            int st = p.getStatus() == null ? 0 : p.getStatus();
            if (st == 1) {
                throw new BusinessException("存在已核收的培养基条码（" + p.getPlateBarcode() + "），请先撤销微生物报告后再取消");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (SamplePlate p : list) {
            int st = p.getStatus() == null ? 0 : p.getStatus();
            if (st == 2) continue;
            p.setStatus(2);
            p.setCancelTime(now);
        }
        this.updateBatchById(list);
    }
}