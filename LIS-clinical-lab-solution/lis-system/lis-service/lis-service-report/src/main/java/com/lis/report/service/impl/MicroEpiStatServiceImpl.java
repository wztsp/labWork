package com.lis.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.api.vo.MicroEpiStatVO;
import com.lis.report.entity.AntibioticSensitivity;
import com.lis.report.entity.MicroAlert;
import com.lis.report.entity.MicroOrganism;
import com.lis.report.entity.MicroReport;
import com.lis.report.mapper.AntibioticSensitivityMapper;
import com.lis.report.mapper.MicroAlertMapper;
import com.lis.report.mapper.MicroOrganismMapper;
import com.lis.report.mapper.MicroReportMapper;
import com.lis.report.service.MicroEpiStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 微生物流行病学统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MicroEpiStatServiceImpl implements MicroEpiStatService {

    private final MicroReportMapper microReportMapper;
    private final MicroOrganismMapper microOrganismMapper;
    private final AntibioticSensitivityMapper antibioticSensitivityMapper;
    private final MicroAlertMapper microAlertMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public MicroEpiStatVO statistics(String beginDate, String endDate) {
        MicroEpiStatVO vo = new MicroEpiStatVO();
        vo.setBeginDate(beginDate);
        vo.setEndDate(endDate);

        LocalDateTime begin = parseBegin(beginDate);
        LocalDateTime end = parseEnd(endDate);

        // 1. 报告总数 (按时间范围)
        LambdaQueryWrapper<MicroReport> reportWrapper = new LambdaQueryWrapper<>();
        if (begin != null) {
            reportWrapper.ge(MicroReport::getCreateTime, begin);
        }
        if (end != null) {
            reportWrapper.le(MicroReport::getCreateTime, end);
        }
        List<MicroReport> reports = microReportMapper.selectList(reportWrapper);
        vo.setTotalReportCount(reports.size());

        // 收集报告ID集合, 用于关联查询
        Set<Long> reportIds = reports.stream().map(MicroReport::getId).collect(Collectors.toSet());

        // 2. 细菌鉴定总数 + 细菌分布
        List<MicroOrganism> organisms = reportIds.isEmpty()
                ? Collections.emptyList()
                : microOrganismMapper.selectList(new LambdaQueryWrapper<MicroOrganism>()
                        .in(MicroOrganism::getMicroReportId, reportIds));
        vo.setTotalOrganismCount(organisms.size());

        Map<String, Long> organismCount = organisms.stream()
                .filter(o -> o.getOrganismName() != null && !o.getOrganismName().isEmpty())
                .collect(Collectors.groupingBy(MicroOrganism::getOrganismName, Collectors.counting()));
        List<MicroEpiStatVO.ItemCount> organismDist = organismCount.entrySet().stream()
                .map(e -> new MicroEpiStatVO.ItemCount(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(MicroEpiStatVO.ItemCount::getCount).reversed())
                .collect(Collectors.toList());
        vo.setOrganismDistribution(organismDist);

        // 3. 标本类型分布
        Map<String, Long> specimenCount = reports.stream()
                .filter(r -> r.getSpecimenType() != null && !r.getSpecimenType().isEmpty())
                .collect(Collectors.groupingBy(MicroReport::getSpecimenType, Collectors.counting()));
        List<MicroEpiStatVO.ItemCount> specimenDist = specimenCount.entrySet().stream()
                .map(e -> new MicroEpiStatVO.ItemCount(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(MicroEpiStatVO.ItemCount::getCount).reversed())
                .collect(Collectors.toList());
        vo.setSpecimenTypeDistribution(specimenDist);

        // 4. 培养部位分布 (前10)
        Map<String, Long> siteCount = reports.stream()
                .filter(r -> r.getCultureSite() != null && !r.getCultureSite().isEmpty())
                .collect(Collectors.groupingBy(MicroReport::getCultureSite, Collectors.counting()));
        List<MicroEpiStatVO.ItemCount> siteDist = siteCount.entrySet().stream()
                .map(e -> new MicroEpiStatVO.ItemCount(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(MicroEpiStatVO.ItemCount::getCount).reversed())
                .limit(10)
                .collect(Collectors.toList());
        vo.setCultureSiteDistribution(siteDist);

        // 5. 药敏结果 + 耐药率统计
        Set<Long> organismIds = organisms.stream().map(MicroOrganism::getId).collect(Collectors.toSet());
        List<AntibioticSensitivity> sensitivities = organismIds.isEmpty()
                ? Collections.emptyList()
                : antibioticSensitivityMapper.selectList(new LambdaQueryWrapper<AntibioticSensitivity>()
                        .in(AntibioticSensitivity::getOrganismId, organismIds));
        vo.setTotalSensitivityCount(sensitivities.size());

        // 药敏结果分布 S/I/R
        Map<String, Integer> resultDist = new LinkedHashMap<>();
        resultDist.put("S", 0);
        resultDist.put("I", 0);
        resultDist.put("R", 0);
        for (AntibioticSensitivity s : sensitivities) {
            if (s.getResult() != null) {
                String r = s.getResult().toUpperCase();
                resultDist.merge(r, 1, Integer::sum);
            }
        }
        vo.setResultDistribution(resultDist);

        // 耐药率统计 (按抗生素名称)
        Map<String, List<AntibioticSensitivity>> byAntibiotic = sensitivities.stream()
                .filter(s -> s.getAntibioticName() != null && !s.getAntibioticName().isEmpty()
                        && s.getResult() != null)
                .collect(Collectors.groupingBy(AntibioticSensitivity::getAntibioticName));
        List<MicroEpiStatVO.ResistanceRate> rates = new ArrayList<>();
        for (Map.Entry<String, List<AntibioticSensitivity>> entry : byAntibiotic.entrySet()) {
            List<AntibioticSensitivity> list = entry.getValue();
            MicroEpiStatVO.ResistanceRate rate = new MicroEpiStatVO.ResistanceRate(entry.getKey());
            rate.setTotalCount(list.size());
            int r = 0, s = 0, i = 0;
            for (AntibioticSensitivity sens : list) {
                String res = sens.getResult() == null ? "" : sens.getResult().toUpperCase();
                switch (res) {
                    case "R" -> r++;
                    case "S" -> s++;
                    case "I" -> i++;
                    default -> {
                    }
                }
            }
            rate.setResistantCount(r);
            rate.setSusceptibleCount(s);
            rate.setIntermediateCount(i);
            rate.setResistanceRate(list.isEmpty() ? 0.0 : Math.round(r * 10000.0 / list.size()) / 100.0);
            rates.add(rate);
        }
        rates.sort(Comparator.comparingInt(MicroEpiStatVO.ResistanceRate::getTotalCount).reversed());
        vo.setResistanceRates(rates);

        // 6. 月度检出趋势
        Map<String, Long> monthlyCount = reports.stream()
                .filter(r -> r.getCreateTime() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getCreateTime().format(MONTH_FMT),
                        Collectors.counting()));
        List<MicroEpiStatVO.ItemCount> monthlyTrend = monthlyCount.entrySet().stream()
                .map(e -> new MicroEpiStatVO.ItemCount(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparing(MicroEpiStatVO.ItemCount::getName))
                .collect(Collectors.toList());
        vo.setMonthlyTrend(monthlyTrend);

        // 7. 特殊耐药预警统计
        List<MicroAlert> alerts = microAlertMapper.selectList(new LambdaQueryWrapper<MicroAlert>()
                .orderByDesc(MicroAlert::getCreateTime));
        vo.setTotalAlertCount(alerts.size());
        Map<String, Long> alertTypeCount = alerts.stream()
                .filter(a -> a.getAlertType() != null)
                .collect(Collectors.groupingBy(MicroAlert::getAlertType, Collectors.counting()));
        List<MicroEpiStatVO.ItemCount> alertDist = alertTypeCount.entrySet().stream()
                .map(e -> new MicroEpiStatVO.ItemCount(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(MicroEpiStatVO.ItemCount::getCount).reversed())
                .collect(Collectors.toList());
        vo.setAlertTypeDistribution(alertDist);

        log.info("微生物流行病学统计完成: reports={}, organisms={}, sensitivities={}, alerts={}",
                vo.getTotalReportCount(), vo.getTotalOrganismCount(),
                vo.getTotalSensitivityCount(), vo.getTotalAlertCount());
        return vo;
    }

    private LocalDateTime parseBegin(String beginDate) {
        if (beginDate == null || beginDate.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(beginDate, DATE_FMT).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseEnd(String endDate) {
        if (endDate == null || endDate.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(endDate, DATE_FMT).atTime(23, 59, 59);
        } catch (Exception e) {
            return null;
        }
    }
}
