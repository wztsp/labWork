package com.lis.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.api.vo.MicroReportVO;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.util.StringUtil;
import com.lis.micro.entity.SamplePlate;
import com.lis.micro.service.SamplePlateService;
import com.lis.report.entity.AntibioticSensitivity;
import com.lis.report.entity.MicroAlert;
import com.lis.report.entity.MicroOrganism;
import com.lis.report.entity.MicroReport;
import com.lis.report.entity.MicroReportSubculture;
import com.lis.report.mapper.AntibioticSensitivityMapper;
import com.lis.report.mapper.MicroOrganismMapper;
import com.lis.report.mapper.MicroReportMapper;
import com.lis.report.mapper.MicroReportSubcultureMapper;
import com.lis.report.service.MicroAlertService;
import com.lis.report.service.MicroExpertRuleService;
import com.lis.report.service.MicroReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 微生物报告服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MicroReportServiceImpl implements MicroReportService {

    private final MicroReportMapper microReportMapper;
    private final MicroOrganismMapper microOrganismMapper;
    private final AntibioticSensitivityMapper antibioticSensitivityMapper;
    private final MicroReportSubcultureMapper microReportSubcultureMapper;
    private final MicroExpertRuleService microExpertRuleService;
    private final MicroAlertService microAlertService;
    private final SamplePlateService samplePlateService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 启动时幂等执行 DDL：确保 lis_micro_report.plate_barcode 列存在。
     * <p>PostgreSQL 原生支持 IF NOT EXISTS；其他数据库降级为 try/catch 忽略重复列错误。</p>
     */
    @PostConstruct
    public void ensurePlateBarcodeColumn() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE lis_micro_report ADD COLUMN IF NOT EXISTS plate_barcode VARCHAR(64)");
            try {
                jdbcTemplate.execute(
                        "CREATE INDEX IF NOT EXISTS idx_lis_micro_report_plate_barcode ON lis_micro_report (plate_barcode)");
            } catch (Exception e) {
                log.warn("创建 idx_lis_micro_report_plate_barcode 索引失败(不阻断启动): {}", e.getMessage());
            }
            log.info("lis_micro_report.plate_barcode 列就绪(幂等DDL)");
        } catch (Exception e) {
            log.warn("lis_micro_report 追加 plate_barcode 列失败(不阻断启动, 实际列可能已存在或非PG方言): {}",
                    e.getMessage());
        }
        // ===== 细菌鉴定 / 药敏结果扩展列(仪器回传结果补齐) =====
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE lis_micro_organism ADD COLUMN IF NOT EXISTS remark VARCHAR(255)");
            log.info("lis_micro_organism.remark 列就绪");
        } catch (Exception e) {
            log.warn("lis_micro_organism 追加 remark 列失败(不阻断启动): {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE lis_antibiotic_sensitivity ADD COLUMN IF NOT EXISTS method VARCHAR(32)");
            jdbcTemplate.execute(
                    "ALTER TABLE lis_antibiotic_sensitivity ADD COLUMN IF NOT EXISTS zone_size INTEGER");
            jdbcTemplate.execute(
                    "ALTER TABLE lis_antibiotic_sensitivity ADD COLUMN IF NOT EXISTS remark VARCHAR(255)");
            log.info("lis_antibiotic_sensitivity 扩展列(method/zone_size/remark)就绪");
        } catch (Exception e) {
            log.warn("lis_antibiotic_sensitivity 追加扩展列失败(不阻断启动): {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO createReport(MicroReportVO vo) {
        if (vo.getSampleId() == null) {
            throw new BusinessException("标本ID不能为空");
        }
        MicroReport report = new MicroReport();
        BeanUtils.copyProperties(vo, report);
        report.setReportNo(generateReportNo());
        report.setReportStatus(MicroReport.STATUS_PRELIMINARY);
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        microReportMapper.insert(report);
        log.info("创建微生物报告: reportNo={}, sampleId={}", report.getReportNo(), report.getSampleId());
        return toVO(report, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO importReport(MicroReportVO vo) {
        if (vo.getSampleId() == null) {
            throw new BusinessException("标本ID不能为空");
        }
        // 创建报告
        MicroReport report = new MicroReport();
        BeanUtils.copyProperties(vo, report);
        report.setReportNo(vo.getReportNo() != null ? vo.getReportNo() : generateReportNo());
        report.setReportStatus(MicroReport.STATUS_IDENTIFICATION);
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        microReportMapper.insert(report);
        log.info("仪器导入创建微生物报告: reportNo={}, sampleId={}", report.getReportNo(), report.getSampleId());

        // 批量添加细菌鉴定 + 药敏结果
        if (vo.getOrganisms() != null) {
            for (MicroReportVO.OrganismVO organismVO : vo.getOrganisms()) {
                MicroOrganism organism = new MicroOrganism();
                BeanUtils.copyProperties(organismVO, organism);
                organism.setMicroReportId(report.getId());
                organism.setId(null);
                organism.setCreateTime(LocalDateTime.now());
                microOrganismMapper.insert(organism);

                List<AntibioticSensitivity> insertedSens = new ArrayList<>();
                if (organismVO.getSensitivities() != null) {
                    for (MicroReportVO.SensitivityVO sensitivityVO : organismVO.getSensitivities()) {
                        AntibioticSensitivity sensitivity = new AntibioticSensitivity();
                        BeanUtils.copyProperties(sensitivityVO, sensitivity);
                        sensitivity.setMicroReportId(report.getId());
                        sensitivity.setOrganismId(organism.getId());
                        sensitivity.setId(null);
                        sensitivity.setCreateTime(LocalDateTime.now());
                        antibioticSensitivityMapper.insert(sensitivity);
                        insertedSens.add(sensitivity);
                    }
                }

                // 耐药规则评估: 检测特殊耐药/危急值
                try {
                    List<MicroAlert> alerts = microExpertRuleService.evaluate(
                            report.getId(), organism, insertedSens);
                    if (!alerts.isEmpty()) {
                        alerts.forEach(alert -> microAlertService.create(alert));
                        log.info("耐药规则触发预警: reportId={}, organism={}, alertCount={}",
                                report.getId(), organism.getOrganismName(), alerts.size());
                    }
                } catch (Exception e) {
                    log.warn("耐药规则评估异常(不阻断导入): reportId={}, err={}", report.getId(), e.getMessage());
                }
            }
        }
        log.info("仪器导入完成: reportId={}, organismCount={}", report.getId(),
                vo.getOrganisms() != null ? vo.getOrganisms().size() : 0);
        return toVO(report, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO pushInstrumentResults(Long reportId, MicroReportVO vo) {
        MicroReport report = microReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + reportId);
        }
        if (MicroReport.STATUS_FINAL == (report.getReportStatus() == null ? 0 : report.getReportStatus())) {
            throw new BusinessException("目标报告已是「最终报告」状态，请先取消最终报告后再接收仪器结果。");
        }
        if (vo.getOrganisms() == null || vo.getOrganisms().isEmpty()) {
            log.info("通信程序回传无 organisms 数据 reportId={}, 跳过", reportId);
            return toVO(report, true);
        }

        // 查询当前报告已有细菌/药敏 (作为匹配去重基准)
        List<MicroOrganism> existingOrganisms = microOrganismMapper.selectList(
                new LambdaQueryWrapper<MicroOrganism>().eq(MicroOrganism::getMicroReportId, reportId));
        Map<String, MicroOrganism> byCode = new java.util.LinkedHashMap<>();
        Map<String, MicroOrganism> byName = new java.util.LinkedHashMap<>();
        existingOrganisms.forEach(o -> {
            if (StringUtil.isNotEmpty(o.getOrganismCode())) byCode.put(o.getOrganismCode(), o);
            if (StringUtil.isNotEmpty(o.getOrganismName())) byName.put(o.getOrganismName(), o);
        });

        for (MicroReportVO.OrganismVO organismVO : vo.getOrganisms()) {
            MicroOrganism organism = null;
            if (StringUtil.isNotEmpty(organismVO.getOrganismCode())) {
                organism = byCode.get(organismVO.getOrganismCode());
            }
            if (organism == null && StringUtil.isNotEmpty(organismVO.getOrganismName())) {
                organism = byName.get(organismVO.getOrganismName());
            }
            boolean isNew = (organism == null);
            if (isNew) {
                organism = new MicroOrganism();
                BeanUtils.copyProperties(organismVO, organism);
                organism.setMicroReportId(reportId);
                organism.setId(null);
                organism.setCreateTime(LocalDateTime.now());
                microOrganismMapper.insert(organism);
                if (StringUtil.isNotEmpty(organism.getOrganismCode())) byCode.put(organism.getOrganismCode(), organism);
                if (StringUtil.isNotEmpty(organism.getOrganismName())) byName.put(organism.getOrganismName(), organism);
            } else {
                // 已存在细菌: 允许覆盖生长量/培养方法等非关键字段
                if (StringUtil.isNotEmpty(organismVO.getQuantity())) organism.setQuantity(organismVO.getQuantity());
                if (StringUtil.isNotEmpty(organismVO.getColonsMethod())) organism.setColonsMethod(organismVO.getColonsMethod());
                if (StringUtil.isNotEmpty(organismVO.getRemark())) organism.setRemark(organismVO.getRemark());
                microOrganismMapper.updateById(organism);
            }

            // upsert 药敏
            List<MicroReportVO.SensitivityVO> sensVOList = organismVO.getSensitivities();
            if (sensVOList == null || sensVOList.isEmpty()) continue;
            List<AntibioticSensitivity> existingSens = antibioticSensitivityMapper.selectList(
                    new LambdaQueryWrapper<AntibioticSensitivity>()
                            .eq(AntibioticSensitivity::getOrganismId, organism.getId()));
            Map<String, AntibioticSensitivity> abByCode = new java.util.LinkedHashMap<>();
            Map<String, AntibioticSensitivity> abByName = new java.util.LinkedHashMap<>();
            existingSens.forEach(s -> {
                if (StringUtil.isNotEmpty(s.getAntibioticCode())) abByCode.put(s.getAntibioticCode(), s);
                if (StringUtil.isNotEmpty(s.getAntibioticName())) abByName.put(s.getAntibioticName(), s);
            });

            List<AntibioticSensitivity> insertedSens = new ArrayList<>(existingSens);
            for (MicroReportVO.SensitivityVO svo : sensVOList) {
                AntibioticSensitivity s = null;
                if (StringUtil.isNotEmpty(svo.getAntibioticCode())) s = abByCode.get(svo.getAntibioticCode());
                if (s == null && StringUtil.isNotEmpty(svo.getAntibioticName())) s = abByName.get(svo.getAntibioticName());
                if (s == null) {
                    s = new AntibioticSensitivity();
                    BeanUtils.copyProperties(svo, s);
                    s.setMicroReportId(reportId);
                    s.setOrganismId(organism.getId());
                    s.setId(null);
                    s.setCreateTime(LocalDateTime.now());
                    antibioticSensitivityMapper.insert(s);
                    insertedSens.add(s);
                    if (StringUtil.isNotEmpty(s.getAntibioticCode())) abByCode.put(s.getAntibioticCode(), s);
                    if (StringUtil.isNotEmpty(s.getAntibioticName())) abByName.put(s.getAntibioticName(), s);
                } else {
                    // 已存在药敏: 优先用回传值覆盖 (仅当回传值非空)
                    if (StringUtil.isNotEmpty(svo.getMicValue())) s.setMicValue(svo.getMicValue());
                    if (StringUtil.isNotEmpty(svo.getResult())) s.setResult(svo.getResult());
                    if (StringUtil.isNotEmpty(svo.getBreakpoint())) s.setBreakpoint(svo.getBreakpoint());
                    if (StringUtil.isNotEmpty(svo.getMethod())) s.setMethod(svo.getMethod());
                    if (StringUtil.isNotEmpty(svo.getRemark())) s.setRemark(svo.getRemark());
                    if (svo.getZoneSize() != null) s.setZoneSize(svo.getZoneSize());
                    antibioticSensitivityMapper.updateById(s);
                }
            }

            // 对该细菌重新评估耐药规则预警
            try {
                List<MicroAlert> alerts = microExpertRuleService.evaluate(reportId, organism, insertedSens);
                if (!alerts.isEmpty()) {
                    alerts.forEach(alert -> microAlertService.create(alert));
                    log.info("通信回传触发耐药规则预警: reportId={}, organism={}, alertCount={}",
                            reportId, organism.getOrganismName(), alerts.size());
                }
            } catch (Exception e) {
                log.warn("耐药规则评估异常(不阻断回传): reportId={}, err={}", reportId, e.getMessage());
            }
        }

        report.setUpdateTime(LocalDateTime.now());
        microReportMapper.updateById(report);
        log.info("通信程序回传结果完成 reportId={}, organismCount={}", reportId, vo.getOrganisms().size());
        return toVO(report, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO pushInstrumentResultsByBarcode(String plateBarcode, String sampleBarcode, MicroReportVO vo) {
        if (StringUtil.isEmpty(plateBarcode) && StringUtil.isEmpty(sampleBarcode)) {
            throw new BusinessException("plateBarcode 和 sampleBarcode 至少提供其一");
        }
        Long reportId = null;
        if (StringUtil.isNotEmpty(plateBarcode)) {
            // 优先 plateBarcode 匹配 (唯一绑定到一条报告)
            MicroReport match = microReportMapper.selectOne(
                    new LambdaQueryWrapper<MicroReport>().eq(MicroReport::getPlateBarcode, plateBarcode).last("limit 1"));
            if (match != null) reportId = match.getId();
        }
        if (reportId == null && StringUtil.isNotEmpty(sampleBarcode)) {
            // 回退: 按 sampleBarcode 匹配最近创建、非最终报告的一条
            List<MicroReport> list = microReportMapper.selectList(
                    new LambdaQueryWrapper<MicroReport>()
                            .eq(MicroReport::getSampleBarcode, sampleBarcode)
                            .ne(MicroReport::getReportStatus, MicroReport.STATUS_FINAL)
                            .orderByDesc(MicroReport::getCreateTime)
                            .last("limit 5"));
            if (list == null || list.isEmpty()) {
                // 允许最终报告也能匹配上（只读最终报告也可能被取消后接收）
                list = microReportMapper.selectList(
                        new LambdaQueryWrapper<MicroReport>()
                                .eq(MicroReport::getSampleBarcode, sampleBarcode)
                                .orderByDesc(MicroReport::getCreateTime)
                                .last("limit 5"));
            }
            if (list != null && !list.isEmpty()) reportId = list.get(0).getId();
        }
        if (reportId == null) {
            throw new BusinessException("未匹配到目标微生物报告。plateBarcode=" + plateBarcode + ", sampleBarcode=" + sampleBarcode);
        }
        return pushInstrumentResults(reportId, vo);
    }

    @Override
    public List<MicroAlert> evaluateExpertRules(Long microReportId) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        List<MicroOrganism> organisms = microOrganismMapper.selectList(
                new LambdaQueryWrapper<MicroOrganism>()
                        .eq(MicroOrganism::getMicroReportId, microReportId));
        List<MicroAlert> allAlerts = new ArrayList<>();
        for (MicroOrganism organism : organisms) {
            List<AntibioticSensitivity> sens = antibioticSensitivityMapper.selectList(
                    new LambdaQueryWrapper<AntibioticSensitivity>()
                            .eq(AntibioticSensitivity::getOrganismId, organism.getId()));
            List<MicroAlert> alerts = microExpertRuleService.evaluate(microReportId, organism, sens);
            if (!alerts.isEmpty()) {
                alerts.forEach(alert -> microAlertService.create(alert));
                allAlerts.addAll(alerts);
            }
        }
        log.info("手动触发耐药规则评估: reportId={}, organismCount={}, alertCount={}",
                microReportId, organisms.size(), allAlerts.size());
        return allAlerts;
    }

    @Override
    public List<MicroAlert> computeRuleHits(Long microReportId) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        List<MicroOrganism> organisms = microOrganismMapper.selectList(
                new LambdaQueryWrapper<MicroOrganism>()
                        .eq(MicroOrganism::getMicroReportId, microReportId));
        List<MicroAlert> hits = new ArrayList<>();
        for (MicroOrganism organism : organisms) {
            List<AntibioticSensitivity> sens = antibioticSensitivityMapper.selectList(
                    new LambdaQueryWrapper<AntibioticSensitivity>()
                            .eq(AntibioticSensitivity::getOrganismId, organism.getId()));
            List<MicroAlert> alerts = microExpertRuleService.evaluate(microReportId, organism, sens);
            if (!alerts.isEmpty()) {
                hits.addAll(alerts);
            }
        }
        return hits;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO.OrganismVO addOrganism(Long microReportId, MicroReportVO.OrganismVO organismVO) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再添加细菌鉴定");
        }
        MicroOrganism organism = new MicroOrganism();
        BeanUtils.copyProperties(organismVO, organism);
        organism.setMicroReportId(microReportId);
        organism.setId(null);
        organism.setCreateTime(LocalDateTime.now());
        microOrganismMapper.insert(organism);
        log.info("添加细菌鉴定: microReportId={}, organismName={}", microReportId, organism.getOrganismName());

        MicroReportVO.OrganismVO result = new MicroReportVO.OrganismVO();
        BeanUtils.copyProperties(organism, result);
        result.setSensitivities(new ArrayList<>());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO.SensitivityVO addSensitivity(Long microReportId, MicroReportVO.SensitivityVO sensitivityVO) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再添加药敏结果");
        }
        if (sensitivityVO.getOrganismId() == null) {
            throw new BusinessException("细菌ID不能为空");
        }
        MicroOrganism organism = microOrganismMapper.selectById(sensitivityVO.getOrganismId());
        if (organism == null || !organism.getMicroReportId().equals(microReportId)) {
            throw new BusinessException("细菌鉴定不存在或不属于该报告: " + sensitivityVO.getOrganismId());
        }
        AntibioticSensitivity sensitivity = new AntibioticSensitivity();
        BeanUtils.copyProperties(sensitivityVO, sensitivity);
        sensitivity.setMicroReportId(microReportId);
        sensitivity.setId(null);
        sensitivity.setCreateTime(LocalDateTime.now());
        antibioticSensitivityMapper.insert(sensitivity);
        log.info("添加药敏结果: microReportId={}, organismId={}, antibiotic={}",
                microReportId, sensitivityVO.getOrganismId(), sensitivityVO.getAntibioticName());

        // 重新评估该细菌的耐药规则
        evaluateExpertRulesForOrganism(microReportId, sensitivityVO.getOrganismId());

        MicroReportVO.SensitivityVO result = new MicroReportVO.SensitivityVO();
        BeanUtils.copyProperties(sensitivity, result);
        return result;
    }

    /**
     * 重新评估指定细菌的耐药规则, 触发特殊耐药/危急值预警
     */
    private void evaluateExpertRulesForOrganism(Long reportId, Long organismId) {
        try {
            MicroOrganism organism = microOrganismMapper.selectById(organismId);
            if (organism == null) {
                return;
            }
            List<AntibioticSensitivity> sens = antibioticSensitivityMapper.selectList(
                    new LambdaQueryWrapper<AntibioticSensitivity>()
                            .eq(AntibioticSensitivity::getOrganismId, organismId));
            List<MicroAlert> alerts = microExpertRuleService.evaluate(reportId, organism, sens);
            if (!alerts.isEmpty()) {
                alerts.forEach(alert -> microAlertService.create(alert));
                log.info("耐药规则触发预警: reportId={}, organism={}, alertCount={}",
                        reportId, organism.getOrganismName(), alerts.size());
            }
        } catch (Exception e) {
            log.warn("耐药规则评估异常(不阻断业务): reportId={}, err={}", reportId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO.OrganismVO updateOrganism(Long microReportId, Long organismId, MicroReportVO.OrganismVO organismVO) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再修改细菌鉴定");
        }
        MicroOrganism organism = microOrganismMapper.selectById(organismId);
        if (organism == null || !organism.getMicroReportId().equals(microReportId)) {
            throw new BusinessException("细菌鉴定不存在或不属于该报告: " + organismId);
        }
        if (organismVO.getOrganismName() != null) organism.setOrganismName(organismVO.getOrganismName());
        if (organismVO.getOrganismCode() != null) organism.setOrganismCode(organismVO.getOrganismCode());
        if (organismVO.getQuantity() != null) organism.setQuantity(organismVO.getQuantity());
        if (organismVO.getColonsMethod() != null) organism.setColonsMethod(organismVO.getColonsMethod());
        microOrganismMapper.updateById(organism);
        log.info("修改细菌鉴定: microReportId={}, organismId={}", microReportId, organismId);

        MicroReportVO.OrganismVO result = new MicroReportVO.OrganismVO();
        BeanUtils.copyProperties(organism, result);
        result.setSensitivities(new ArrayList<>());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrganism(Long microReportId, Long organismId) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再删除细菌鉴定");
        }
        MicroOrganism organism = microOrganismMapper.selectById(organismId);
        if (organism == null || !organism.getMicroReportId().equals(microReportId)) {
            throw new BusinessException("细菌鉴定不存在或不属于该报告: " + organismId);
        }
        // 先删除关联的药敏结果
        antibioticSensitivityMapper.delete(
                new LambdaQueryWrapper<AntibioticSensitivity>()
                        .eq(AntibioticSensitivity::getOrganismId, organismId));
        microOrganismMapper.deleteById(organismId);
        log.info("删除细菌鉴定及关联药敏: microReportId={}, organismId={}", microReportId, organismId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO.SensitivityVO updateSensitivity(Long microReportId, Long sensitivityId, MicroReportVO.SensitivityVO sensitivityVO) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再修改药敏结果");
        }
        AntibioticSensitivity sensitivity = antibioticSensitivityMapper.selectById(sensitivityId);
        if (sensitivity == null || !sensitivity.getMicroReportId().equals(microReportId)) {
            throw new BusinessException("药敏结果不存在或不属于该报告: " + sensitivityId);
        }
        if (sensitivityVO.getAntibioticName() != null) sensitivity.setAntibioticName(sensitivityVO.getAntibioticName());
        if (sensitivityVO.getAntibioticCode() != null) sensitivity.setAntibioticCode(sensitivityVO.getAntibioticCode());
        if (sensitivityVO.getMicValue() != null) sensitivity.setMicValue(sensitivityVO.getMicValue());
        if (sensitivityVO.getResult() != null) sensitivity.setResult(sensitivityVO.getResult());
        if (sensitivityVO.getBreakpoint() != null) sensitivity.setBreakpoint(sensitivityVO.getBreakpoint());
        antibioticSensitivityMapper.updateById(sensitivity);
        log.info("修改药敏结果: microReportId={}, sensitivityId={}", microReportId, sensitivityId);

        // 重新评估该细菌的耐药规则
        if (sensitivity.getOrganismId() != null) {
            evaluateExpertRulesForOrganism(microReportId, sensitivity.getOrganismId());
        }

        MicroReportVO.SensitivityVO result = new MicroReportVO.SensitivityVO();
        BeanUtils.copyProperties(sensitivity, result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSensitivity(Long microReportId, Long sensitivityId) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再删除药敏结果");
        }
        AntibioticSensitivity sensitivity = antibioticSensitivityMapper.selectById(sensitivityId);
        if (sensitivity == null || !sensitivity.getMicroReportId().equals(microReportId)) {
            throw new BusinessException("药敏结果不存在或不属于该报告: " + sensitivityId);
        }
        antibioticSensitivityMapper.deleteById(sensitivityId);
        log.info("删除药敏结果: microReportId={}, sensitivityId={}", microReportId, sensitivityId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO publishReport(Long microReportId, int targetStatus, Long auditorId) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (targetStatus < MicroReport.STATUS_PRELIMINARY || targetStatus > MicroReport.STATUS_FINAL) {
            throw new BusinessException("无效的目标报告状态: " + targetStatus);
        }
        if (report.getReportStatus() != null && targetStatus <= report.getReportStatus()) {
            throw new BusinessException("目标状态必须高于当前状态, 当前="
                    + MicroReport.statusText(report.getReportStatus()));
        }
        report.setReportStatus(targetStatus);
        report.setAuditorId(auditorId);
        report.setAuditTime(LocalDateTime.now());
        if (targetStatus == MicroReport.STATUS_FINAL) {
            report.setPublishTime(LocalDateTime.now());
        }
        report.setUpdateTime(LocalDateTime.now());
        microReportMapper.updateById(report);
        log.info("发布微生物报告: id={}, targetStatus={}, auditorId={}",
                microReportId, MicroReport.statusText(targetStatus), auditorId);
        return toVO(report, true);
    }

    @Override
    public MicroReportVO getById(Long id) {
        MicroReport report = microReportMapper.selectById(id);
        if (report == null) {
            return null;
        }
        return toVO(report, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO saveGramStain(Long microReportId, MicroReportVO vo) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再修改革兰氏染色结果");
        }
        report.setGramStainResult(vo.getGramStainResult());
        report.setGramStainRemark(vo.getGramStainRemark());
        report.setGramStainOperator(vo.getGramStainOperator());
        report.setGramStainTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        microReportMapper.updateById(report);
        log.info("保存革兰氏染色结果: id={}, operator={}", microReportId, vo.getGramStainOperator());
        return toVO(report, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO saveNegativeReport(Long microReportId, MicroReportVO vo) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再修改阴性报告");
        }
        report.setIsNegative(vo.getIsNegative() == null ? 1 : vo.getIsNegative());
        report.setNegativeRemark(vo.getNegativeRemark());
        report.setNegativeOperator(vo.getNegativeOperator());
        report.setNegativeTime(LocalDateTime.now());
        // 阴性报告直接置为最终报告状态
        if (report.getIsNegative() != null && report.getIsNegative() == 1) {
            report.setReportStatus(MicroReport.STATUS_FINAL);
            report.setPublishTime(LocalDateTime.now());
        }
        report.setUpdateTime(LocalDateTime.now());
        microReportMapper.updateById(report);
        log.info("保存阴性报告: id={}, isNegative={}, operator={}",
                microReportId, report.getIsNegative(), vo.getNegativeOperator());
        return toVO(report, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO saveBloodCulture(Long microReportId, MicroReportVO vo) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再修改血培养信息");
        }
        report.setBottleNo(vo.getBottleNo());
        report.setBottleType(vo.getBottleType());
        report.setVolume(vo.getVolume());
        report.setInoculateTime(vo.getInoculateTime());
        report.setAlarmTime(vo.getAlarmTime());
        report.setTtpHours(vo.getTtpHours());
        report.setBloodCultureResult(vo.getBloodCultureResult());
        report.setUpdateTime(LocalDateTime.now());
        microReportMapper.updateById(report);
        log.info("保存血培养信息: id={}, bottleNo={}, result={}",
                microReportId, vo.getBottleNo(), vo.getBloodCultureResult());
        return toVO(report, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO.SubcultureVO addSubculture(Long microReportId, MicroReportVO.SubcultureVO subcultureVO) {
        MicroReport report = microReportMapper.selectById(microReportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + microReportId);
        }
        if (report.getReportStatus() != null && report.getReportStatus() == MicroReport.STATUS_FINAL) {
            throw new BusinessException("最终报告已发布, 不可再添加转种记录");
        }
        MicroReportSubculture subculture = new MicroReportSubculture();
        BeanUtils.copyProperties(subcultureVO, subculture);
        subculture.setMicroReportId(microReportId);
        subculture.setId(null);
        subculture.setCreateTime(LocalDateTime.now());
        microReportSubcultureMapper.insert(subculture);
        log.info("添加转种记录: microReportId={}, medium={}", microReportId, subculture.getMedium());

        MicroReportVO.SubcultureVO result = new MicroReportVO.SubcultureVO();
        BeanUtils.copyProperties(subculture, result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSubculture(Long microReportId, Long subcultureId) {
        MicroReportSubculture subculture = microReportSubcultureMapper.selectById(subcultureId);
        if (subculture == null || !subculture.getMicroReportId().equals(microReportId)) {
            throw new BusinessException("转种记录不存在或不属于该报告: " + subcultureId);
        }
        microReportSubcultureMapper.deleteById(subcultureId);
        log.info("删除转种记录: microReportId={}, subcultureId={}", microReportId, subcultureId);
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 兼容日期解析: 支持 yyyy-MM-dd 和 yyyy-MM-dd HH:mm:ss 两种格式
     * @param s 日期字符串
     * @param isStart 仅日期时 true=00:00:00, false=23:59:59
     */
    private LocalDateTime parseDate(String s, boolean isStart) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        try {
            if (v.length() > 10) return LocalDateTime.parse(v, DATETIME_FMT);
            LocalDate d = LocalDate.parse(v, DATE_FMT);
            return isStart ? d.atStartOfDay() : d.atTime(23, 59, 59);
        } catch (Exception e) {
            log.warn("[micro-queryPage] 解析日期失败: {}", v);
            return null;
        }
    }

    @Override
    public PageResult<MicroReportVO> queryPage(int current, int size, String patientName, Integer reportStatus,
                                               String beginDate, String endDate,
                                               String keyword, String reportNo, String sampleBarcode, Long patientId) {
        LambdaQueryWrapper<MicroReport> wrapper = new LambdaQueryWrapper<>();
        if (StringUtil.isNotEmpty(patientName)) {
            wrapper.like(MicroReport::getPatientName, patientName);
        }
        if (reportStatus != null) {
            wrapper.eq(MicroReport::getReportStatus, reportStatus);
        }
        if (patientId != null) {
            wrapper.eq(MicroReport::getPatientId, patientId);
        }
        if (StringUtil.isNotEmpty(reportNo)) {
            wrapper.like(MicroReport::getReportNo, reportNo);
        }
        if (StringUtil.isNotEmpty(sampleBarcode)) {
            wrapper.like(MicroReport::getSampleBarcode, sampleBarcode);
        }
        // keyword: 患者姓名 / 报告号 / 标本条码 三字段模糊 OR
        if (StringUtil.isNotEmpty(keyword)) {
            final String kw = "%" + keyword.trim() + "%";
            wrapper.and(w -> w
                    .like(MicroReport::getPatientName, keyword.trim())
                    .or().like(MicroReport::getReportNo, keyword.trim())
                    .or().like(MicroReport::getSampleBarcode, keyword.trim()));
        }
        LocalDateTime beginDt = parseDate(beginDate, true);
        LocalDateTime endDt = parseDate(endDate, false);
        if (beginDt != null) {
            wrapper.ge(MicroReport::getCreateTime, beginDt);
        }
        if (endDt != null) {
            wrapper.le(MicroReport::getCreateTime, endDt);
        }
        wrapper.orderByDesc(MicroReport::getCreateTime);
        Page<MicroReport> page = microReportMapper.selectPage(new Page<>(current, size), wrapper);
        List<MicroReportVO> records = page.getRecords().stream()
                .map(r -> toVO(r, false))
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO receiveByPlateBarcode(String plateBarcode) {
        if (StringUtil.isEmpty(plateBarcode)) {
            throw new BusinessException("培养基条码不能为空");
        }
        // 1. 通过 SamplePlateService 按培养皿条码查询（MyBatis-Plus 直连，避免手写 SQL 别名/大小写问题）
        SamplePlate plate = samplePlateService.getOne(
                new LambdaQueryWrapper<SamplePlate>()
                        .eq(SamplePlate::getPlateBarcode, plateBarcode.trim())
                        .last("LIMIT 1"));
        if (plate == null) {
            throw new BusinessException("未找到培养基条码对应的标本: " + plateBarcode);
        }
        // 状态校验: 已作废 / 已核收 的培养基条码均不允许再次核收
        int plateStatus = plate.getStatus() == null ? 0 : plate.getStatus();
        if (plateStatus == 2) {
            throw new BusinessException("该培养基条码已作废, 不能核收微生物报告: " + plateBarcode);
        }
        if (plateStatus == 1) {
            throw new BusinessException("该培养基条码已核收, 请不要重复核收: " + plateBarcode);
        }
        Long sampleId = plate.getSampleId();
        String sampleBarcode = plate.getSampleBarcode();
        String patientName = plate.getPatientName() != null ? plate.getPatientName() : "未知患者";

        // 通过 sampleId 关联查询标本表获取标本类型等信息
        String specimenType = null;
        Long patientId = null;
        if (sampleId != null) {
            try {
                List<Map<String, Object>> sampleRows = jdbcTemplate.queryForList(
                    "SELECT sample_type, patient_id, patient_name FROM lis_sample WHERE id = ? LIMIT 1",
                    sampleId);
                if (sampleRows != null && !sampleRows.isEmpty()) {
                    Map<String, Object> sr = sampleRows.get(0);
                    Object st = sr.get("sample_type");
                    if (st != null) specimenType = st.toString();
                    Object pid = sr.get("patient_id");
                    if (pid != null) patientId = ((Number) pid).longValue();
                    Object pn = sr.get("patient_name");
                    if ((patientName == null || "未知患者".equals(patientName)) && pn != null) {
                        patientName = pn.toString();
                    }
                }
            } catch (Exception e) {
                log.warn("通过 sampleId={} 查询标本表失败: {}", sampleId, e.getMessage());
            }
        }
        if (sampleId == null) {
            throw new BusinessException("培养基条码未绑定标本，请先在标本登记页面登记");
        }

        // 2. 如果该培养条码已经核收过一份微生物报告 -> 阻断并提示, 不再静默返回旧报告
        LambdaQueryWrapper<MicroReport> existWrapper = new LambdaQueryWrapper<MicroReport>()
                .eq(MicroReport::getPlateBarcode, plateBarcode.trim())
                .orderByDesc(MicroReport::getCreateTime)
                .last("LIMIT 1");
        MicroReport exist = microReportMapper.selectOne(existWrapper);
        if (exist != null) {
            // 补一次 plate 状态, 避免历史异常数据 plate 仍为 0 但报告已存在
            updatePlateStatus(plateBarcode, 1);
            throw new BusinessException("该培养基条码已核收(报告号 " + exist.getReportNo() + "), 请不要重复核收: " + plateBarcode);
        }

        // 兼容历史数据: plateBarcode 为空的旧报告若该标本只有一份, 仍按 sampleId 兜底匹配一次,
        // 避免同一标本既存在旧模式报告又被新模式重复核收. 但如果 plateStatus=0 且已存在历史报告 -> 提示已核收
        List<MicroReport> oldStyleReports = microReportMapper.selectList(
                new LambdaQueryWrapper<MicroReport>()
                        .eq(MicroReport::getSampleId, sampleId)
                        .isNull(MicroReport::getPlateBarcode)
                        .orderByDesc(MicroReport::getCreateTime)
                        .last("LIMIT 1"));
        if (oldStyleReports != null && !oldStyleReports.isEmpty()) {
            MicroReport old = oldStyleReports.get(0);
            old.setPlateBarcode(plateBarcode.trim());
            old.setUpdateTime(LocalDateTime.now());
            microReportMapper.updateById(old);
            updatePlateStatus(plateBarcode, 1);
            throw new BusinessException("该培养基条码已核收(历史报告 " + old.getReportNo() + "), 请不要重复核收: " + plateBarcode);
        }

        // 3. 自动创建微生物报告(初步报告) — 每条培养条码独立一份
        MicroReport report = new MicroReport();
        report.setSampleId(sampleId);
        report.setPatientName(patientName);
        report.setSpecimenType(specimenType);
        report.setReportNo(generateReportNo());
        report.setReportStatus(MicroReport.STATUS_PRELIMINARY);
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        report.setSampleBarcode(sampleBarcode);
        report.setPlateBarcode(plateBarcode.trim());
        if (patientId != null) report.setPatientId(patientId);
        microReportMapper.insert(report);
        log.info("培养基条码 {} 核收成功, 自动创建微生物报告 {} sampleId={}", plateBarcode, report.getReportNo(), sampleId);

        // 更新培养皿条码状态为已核收
        updatePlateStatus(plateBarcode, 1);

        return toVO(report, true);
    }

    private void updatePlateStatus(String plateBarcode, int status) {
        try {
            SamplePlate p = samplePlateService.getOne(
                    new LambdaQueryWrapper<SamplePlate>()
                            .eq(SamplePlate::getPlateBarcode, plateBarcode)
                            .last("LIMIT 1"));
            if (p != null) {
                p.setStatus(status);
                samplePlateService.updateById(p);
            }
        } catch (Exception e) {
            log.warn("更新培养皿状态失败: plateBarcode={}, err={}", plateBarcode, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReceive(Long reportId) {
        if (reportId == null) throw new BusinessException("报告ID不能为空");
        MicroReport report = microReportMapper.selectById(reportId);
        if (report == null) throw new BusinessException("微生物报告不存在");
        int st = report.getReportStatus() == null ? 0 : report.getReportStatus();
        if (st != MicroReport.STATUS_PRELIMINARY) {
            throw new BusinessException("当前报告状态为「" + MicroReport.statusText(report.getReportStatus()) +
                    "」，仅允许撤销「初步报告」；已发布/最终报告请走作废流程。");
        }
        Long sampleId = report.getSampleId();
        String sampleBarcode = report.getSampleBarcode();
        String reportPlateBarcode = report.getPlateBarcode();

        // 1. 回滚关联培养基条码状态为未核收 (0)
        //    新模式: 只回滚本报告绑定的那一条 plateBarcode, 同标本下其他培养条码不影响
        //    历史兼容: 若 plateBarcode 为空, 则把该标本下仍未被其他报告占用的已核收条码回滚为未核收
        try {
            if (StringUtil.isNotEmpty(reportPlateBarcode)) {
                SamplePlate p = samplePlateService.getOne(
                        new LambdaQueryWrapper<SamplePlate>()
                                .eq(SamplePlate::getPlateBarcode, reportPlateBarcode.trim())
                                .last("LIMIT 1"));
                if (p != null && (p.getStatus() == null || p.getStatus() == 1)) {
                    p.setStatus(0);
                    samplePlateService.updateById(p);
                }
            } else if (sampleId != null) {
                List<SamplePlate> plates = samplePlateService.list(
                        new LambdaQueryWrapper<SamplePlate>().eq(SamplePlate::getSampleId, sampleId));
                List<String> occupiedBarcodes = microReportMapper.selectList(
                                new LambdaQueryWrapper<MicroReport>()
                                        .eq(MicroReport::getSampleId, sampleId)
                                        .ne(MicroReport::getId, reportId)
                                        .isNotNull(MicroReport::getPlateBarcode))
                        .stream().map(MicroReport::getPlateBarcode).collect(Collectors.toList());
                for (SamplePlate p : plates) {
                    int pst = p.getStatus() == null ? 0 : p.getStatus();
                    if (pst == 1 && !occupiedBarcodes.contains(p.getPlateBarcode())) {
                        p.setStatus(0);
                    }
                }
                samplePlateService.updateBatchById(plates);
            } else if (sampleBarcode != null && !sampleBarcode.isEmpty()) {
                List<SamplePlate> plates = samplePlateService.listBySampleBarcode(sampleBarcode);
                for (SamplePlate p : plates) {
                    int pst = p.getStatus() == null ? 0 : p.getStatus();
                    if (pst == 1) p.setStatus(0);
                }
                samplePlateService.updateBatchById(plates);
            }
        } catch (Exception e) {
            log.warn("撤销核收时回滚培养基状态失败 sampleId={} plateBarcode={}: {}",
                    sampleId, reportPlateBarcode, e.getMessage());
        }

        // 2. 删除细菌鉴定明细、药敏结果、转种记录等（物理删除，彻底回滚核收）
        try {
            List<MicroOrganism> organisms = microOrganismMapper.selectList(
                    new LambdaQueryWrapper<MicroOrganism>().eq(MicroOrganism::getMicroReportId, reportId));
            for (MicroOrganism o : organisms) {
                antibioticSensitivityMapper.delete(
                        new LambdaQueryWrapper<AntibioticSensitivity>()
                                .eq(AntibioticSensitivity::getMicroReportId, reportId)
                                .eq(AntibioticSensitivity::getOrganismId, o.getId()));
            }
            microOrganismMapper.delete(
                    new LambdaQueryWrapper<MicroOrganism>().eq(MicroOrganism::getMicroReportId, reportId));
            microReportSubcultureMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MicroReportSubculture>()
                            .eq(MicroReportSubculture::getMicroReportId, reportId));
        } catch (Exception e) {
            log.warn("清理微生物报告子表失败 reportId={}: {}", reportId, e.getMessage());
        }

        // 3. 删除主报告记录 (物理删除，标本后续可再次通过培养基条码重新核收)
        microReportMapper.deleteById(reportId);
        log.info("撤销核收成功 reportId={}, reportNo={}, sampleId={}, sampleBarcode={}",
                reportId, report.getReportNo(), sampleId, sampleBarcode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportVO revokeFinalReport(Long reportId) {
        MicroReport report = microReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("微生物报告不存在: " + reportId);
        }
        int st = report.getReportStatus() == null ? 0 : report.getReportStatus();
        if (st != MicroReport.STATUS_FINAL) {
            throw new BusinessException("当前报告状态为「" + MicroReport.statusText(report.getReportStatus()) +
                    "」，仅允许取消「最终报告」。");
        }
        try {
            // 最终报告回退为鉴定报告，恢复编辑与重新发布能力
            // 显式 set null 清空审核/发布时间（updateById 默认忽略 null 字段，会导致发布时间残留）
            LocalDateTime now = LocalDateTime.now();
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MicroReport> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            uw.eq(MicroReport::getId, reportId)
                    .set(MicroReport::getReportStatus, MicroReport.STATUS_IDENTIFICATION)
                    .set(MicroReport::getAuditorId, null)
                    .set(MicroReport::getAuditTime, null)
                    .set(MicroReport::getPublishTime, null)
                    .set(MicroReport::getUpdateTime, now);
            microReportMapper.update(null, uw);
            report.setReportStatus(MicroReport.STATUS_IDENTIFICATION);
            report.setAuditorId(null);
            report.setAuditTime(null);
            report.setPublishTime(null);
            report.setUpdateTime(now);
            log.info("取消最终报告成功 reportId={}, reportNo={}, 回退为鉴定报告", reportId, report.getReportNo());
        } catch (Exception e) {
            log.error("取消最终报告失败 reportId={}", reportId, e);
            throw new BusinessException("取消最终报告失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return toVO(report, true);
    }

    /**
     * 实体转 VO
     */
    private MicroReportVO toVO(MicroReport report, boolean withDetails) {
        MicroReportVO vo = new MicroReportVO();
        try {
            BeanUtils.copyProperties(report, vo);
            vo.setReportStatusText(MicroReport.statusText(report.getReportStatus()));
        } catch (Exception e) {
            log.warn("BeanUtils 复制报告失败 id={}: {}", report.getId(), e.getMessage());
        }
        if (withDetails) {
            // 补充标本信息: 采集条码/标本号/培养条码/申请项目/采集时间/采集人/核收时间/申请科室
            enrichSampleInfo(vo);
            try {
                List<MicroOrganism> organisms = microOrganismMapper.selectList(
                        new LambdaQueryWrapper<MicroOrganism>()
                                .eq(MicroOrganism::getMicroReportId, report.getId()));
                List<MicroReportVO.OrganismVO> organismVOs = new ArrayList<>();
                for (MicroOrganism o : organisms) {
                    MicroReportVO.OrganismVO ovo = new MicroReportVO.OrganismVO();
                    try {
                        BeanUtils.copyProperties(o, ovo);
                    } catch (Exception e) {
                        log.warn("复制细菌失败 id={}: {}", o.getId(), e.getMessage());
                    }
                    try {
                        List<AntibioticSensitivity> sensitivities = antibioticSensitivityMapper.selectList(
                                new LambdaQueryWrapper<AntibioticSensitivity>()
                                        .eq(AntibioticSensitivity::getOrganismId, o.getId()));
                        List<MicroReportVO.SensitivityVO> sensitivityVOs = sensitivities.stream().map(s -> {
                            MicroReportVO.SensitivityVO svo = new MicroReportVO.SensitivityVO();
                            try {
                                BeanUtils.copyProperties(s, svo);
                            } catch (Exception e) {
                                log.warn("复制药敏失败 id={}: {}", s.getId(), e.getMessage());
                            }
                            return svo;
                        }).collect(Collectors.toList());
                        ovo.setSensitivities(sensitivityVOs);
                    } catch (Exception e) {
                        log.warn("查询药敏列表失败 organismId={}: {}", o.getId(), e.getMessage());
                    }
                    organismVOs.add(ovo);
                }
                vo.setOrganisms(organismVOs);
            } catch (Exception e) {
                log.warn("查询细菌列表失败 reportId={}: {}", report.getId(), e.getMessage());
            }
            // 查询转种记录
            try {
                List<MicroReportSubculture> subcultures = microReportSubcultureMapper.selectList(
                        new LambdaQueryWrapper<MicroReportSubculture>()
                                .eq(MicroReportSubculture::getMicroReportId, report.getId()));
                List<MicroReportVO.SubcultureVO> subcultureVOs = subcultures.stream().map(s -> {
                    MicroReportVO.SubcultureVO svo = new MicroReportVO.SubcultureVO();
                    try {
                        BeanUtils.copyProperties(s, svo);
                    } catch (Exception e) {
                        log.warn("复制转种记录失败 id={}: {}", s.getId(), e.getMessage());
                    }
                    return svo;
                }).collect(Collectors.toList());
                vo.setSubcultures(subcultureVOs);
            } catch (Exception e) {
                log.warn("查询转种记录失败 reportId={}: {}", report.getId(), e.getMessage());
            }
        }
        return vo;
    }

    /**
     * 补充标本信息: 采集条码/标本号/培养条码/申请项目/采集时间/采集人/核收时间/申请科室
     * (数据来自 lis_sample / lis_staff / lis_sample_order_item / lis_order_item / lis_sample_plate)
     */
    private void enrichSampleInfo(MicroReportVO vo) {
        Long sampleId = vo.getSampleId();
        if (sampleId == null) return;
        try {
            // 1. 标本基本信息 + 采集人(姓名)
            jdbcTemplate.query(
                    "SELECT s.sample_barcode, s.sample_no, s.sample_type, s.department, s.collect_time, s.receive_time, " +
                            "st.name AS collector_name " +
                            "FROM lis_sample s " +
                            "LEFT JOIN lis_staff st ON st.id = s.collector_id " +
                            "WHERE s.id = ?",
                    rs -> {
                        vo.setSampleNo(rs.getString("sample_no"));
                        if (StringUtil.isEmpty(vo.getSampleBarcode())) vo.setSampleBarcode(rs.getString("sample_barcode"));
                        if (StringUtil.isEmpty(vo.getSpecimenType())) vo.setSpecimenType(rs.getString("sample_type"));
                        vo.setDepartment(rs.getString("department"));
                        if (rs.getTimestamp("collect_time") != null) vo.setCollectTime(rs.getTimestamp("collect_time").toLocalDateTime());
                        if (rs.getTimestamp("receive_time") != null) vo.setReceiveTime(rs.getTimestamp("receive_time").toLocalDateTime());
                        vo.setCollectorName(rs.getString("collector_name"));
                    }, sampleId);
            // 2. 培养条码 (培养基条码)
            //    - 当前报告已通过 BeanUtils.copyProperties 自带 plateBarcode (每条报告唯一一次核收)
            //    - 这里再补充该标本下其他培养条码(顿号拼接)以便列表核对
            try {
                String plates;
                try {
                    plates = jdbcTemplate.queryForObject(
                            "SELECT COALESCE(string_agg(plate_barcode, '、' ORDER BY plate_barcode), '') FROM lis_sample_plate WHERE sample_id = ? AND status IN (0,1)",
                            String.class, sampleId);
                } catch (Exception e) {
                    plates = null;
                    log.warn("查询微生物培养条码列表失败 sampleId={}: {}", sampleId, e.getMessage());
                }
                // plateBarcodes: 如果标本下就只一个条码 且与本报告绑定条码一致, 就不必再重复写一次
                String self = vo.getPlateBarcode();
                if (StringUtil.isNotEmpty(self)) {
                    if (StringUtil.isEmpty(plates) || self.equalsIgnoreCase(plates == null ? "" : plates.trim())) {
                        vo.setPlateBarcodes(self);
                    } else {
                        vo.setPlateBarcodes(plates);
                    }
                } else if (StringUtil.isNotEmpty(plates)) {
                    vo.setPlateBarcodes(plates);
                }
            } catch (Exception ignore) {}
            // 3. 申请项目 (优先关联中间表, 降级用 order_id 查 lis_order_item)
            try {
                String items = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(string_agg(DISTINCT oi.test_item_name, '、' ORDER BY oi.test_item_name), '') " +
                                "FROM lis_sample_order_item soi " +
                                "JOIN lis_order_item oi ON soi.order_item_id = oi.id " +
                                "WHERE soi.sample_id = ?",
                        String.class, sampleId);
                if (StringUtil.isEmpty(items)) {
                    items = jdbcTemplate.queryForObject(
                            "SELECT COALESCE(string_agg(DISTINCT oi.test_item_name, '、' ORDER BY oi.test_item_name), '') " +
                                    "FROM lis_order_item oi " +
                                    "WHERE oi.order_id = (SELECT order_id FROM lis_sample WHERE id = ?)",
                            String.class, sampleId);
                }
                vo.setApplyItems(items);
            } catch (Exception e) {
                log.warn("查询微生物申请项目失败 sampleId={}: {}", sampleId, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("补充微生物标本信息失败 sampleId={}: {}", sampleId, e.getMessage());
        }
    }

    private String generateReportNo() {
        return "MIC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + (int) (Math.random() * 9000 + 1000);
    }
}
