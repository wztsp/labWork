package com.lis.report.service.impl;

import com.lis.micro.entity.ResistanceRule;
import com.lis.micro.service.ResistanceRuleService;
import com.lis.micro.util.CodeListUtil;
import com.lis.report.entity.AntibioticSensitivity;
import com.lis.report.entity.MicroAlert;
import com.lis.report.entity.MicroOrganism;
import com.lis.report.service.MicroExpertRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 耐药规则引擎: 统一可配置规则的数据驱动判定 (原专家规则已并入)。
 *
 * <p>优先读取数据库中的可配置规则(lis_resistance_rule, 内置 MRSA/VRE/CRE/CRAB/最后防线耐药等种子,
 * 也支持用户自定义), 对每个细菌的药敏结果按 matchMode(ANY_RESISTANT / EXACT) 判定;
 * 另保留 ESBL(组合/互斥)、MDR(≥3类耐药) 两个内置计算规则(无法用简单配置表达)。
 * 细菌匹配采用「编码严格命中 OR 名称关键字别名命中」双通道, 兼容不同厂商字典编码(如 SAU / STAUR / 金葡)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MicroExpertRuleServiceImpl implements MicroExpertRuleService {

    private final ResistanceRuleService resistanceRuleService;

    /** 常见细菌编码 → 名称关键字别名映射 (兼容不同仪器字典: 编码或名称) */
    private static final Map<String, List<String>> BACTERIA_CODE_ALIAS = new HashMap<>();
    static {
        // 金黄色葡萄球菌 (SAU = WHONET 标准)
        BACTERIA_CODE_ALIAS.put("SAU", Arrays.asList("金黄色葡萄球菌", "金葡", "Staphylococcus aureus", "SAUR", "STAUR", "STAU"));
        // 粪肠球菌
        BACTERIA_CODE_ALIAS.put("EFA", Arrays.asList("粪肠球菌", "Enterococcus faecalis", "EFM", "ECF"));
        // 屎肠球菌 (别名兜底)
        BACTERIA_CODE_ALIAS.put("EFM", Arrays.asList("屎肠球菌", "Enterococcus faecium", "EFS"));
        // 大肠埃希菌
        BACTERIA_CODE_ALIAS.put("ECO", Arrays.asList("大肠埃希菌", "大肠杆菌", "Escherichia coli", "ESCCO"));
        // 肺炎克雷伯菌
        BACTERIA_CODE_ALIAS.put("KPN", Arrays.asList("肺炎克雷伯菌", "Klebsiella pneumoniae", "KLEPN"));
        // 产酸克雷伯菌
        BACTERIA_CODE_ALIAS.put("ECL", Arrays.asList("产酸克雷伯菌", "Klebsiella oxytoca", "KLEO"));
        // 阴沟肠杆菌
        BACTERIA_CODE_ALIAS.put("ARE", Arrays.asList("阴沟肠杆菌", "Enterobacter cloacae", "ENTC"));
        // 弗劳地枸橼酸杆菌
        BACTERIA_CODE_ALIAS.put("CFF", Arrays.asList("弗劳地枸橼酸杆菌", "Citrobacter freundii"));
        // 奇异变形杆菌
        BACTERIA_CODE_ALIAS.put("PMI", Arrays.asList("奇异变形杆菌", "Proteus mirabilis"));
        // 粘质沙雷菌
        BACTERIA_CODE_ALIAS.put("SMA", Arrays.asList("粘质沙雷菌", "Serratia marcescens"));
        // 鲍曼不动杆菌
        BACTERIA_CODE_ALIAS.put("ABA", Arrays.asList("鲍曼不动杆菌", "Acinetobacter baumannii", "ACB"));
    }

    /** 第三代头孢菌素 + 氨曲南关键词 (中英文) */
    private static final String[] CEPH3_AND_AZTREONAM = {
            "头孢曲松", "头孢他啶", "头孢噻肟", "氨曲南",
            "Ceftriaxone", "Ceftazidime", "Cefotaxime", "Aztreonam"
    };

    /** β-内酰胺酶抑制剂复方关键词 (中英文) */
    private static final String[] INHIBITOR_COMBOS = {
            "头孢哌酮舒巴坦", "哌拉西林他唑巴坦", "阿莫西林克拉维酸",
            "Cefoperazone-Sulbactam", "Piperacillin-Tazobactam", "Amoxicillin-Clavulanate"
    };

    @Override
    public List<MicroAlert> evaluate(Long reportId, MicroOrganism organism, List<AntibioticSensitivity> sensitivities) {
        List<MicroAlert> alerts = new ArrayList<>();
        if (organism == null || sensitivities == null || sensitivities.isEmpty()) {
            return alerts;
        }
        String name = organism.getOrganismName();
        if (name == null || name.isEmpty()) {
            return alerts;
        }
        SensitivityLookup lookup = new SensitivityLookup(sensitivities);

        // ===== 1. 数据驱动: 可配置耐药/耐药规则 (MRSA/CRE/VRE/CRAB/最后防线/用户自定义) =====
        List<ResistanceRule> rules;
        try {
            rules = resistanceRuleService.listAllEnabled();
        } catch (Exception e) {
            log.warn("加载耐药规则失败(回退略过可配置规则): {}", e.getMessage());
            rules = Collections.emptyList();
        }
        if (rules != null) {
            for (ResistanceRule rule : rules) {
                if (ruleMatches(organism, rule, lookup)) {
                    alerts.add(buildAlert(reportId, organism, rule));
                }
            }
        }

        // ===== 2. 内置计算规则: ESBL (组合/互斥表型, 无法用简单配置表达) =====
        if (containsAny(name, "大肠埃希菌", "肺炎克雷伯菌", "产酸克雷伯菌",
                "Escherichia coli", "Klebsiella")) {
            boolean ceph3OrAztreonamR = lookup.isAnyResistant(CEPH3_AND_AZTREONAM);
            boolean carbapenemR = lookup.isAnyResistant(new String[]{
                    "亚胺培南", "美罗培南", "厄他培南", "Imipenem", "Meropenem", "Ertapenem"});
            boolean carbapenemS = lookup.isAnySusceptible(new String[]{
                    "亚胺培南", "美罗培南", "厄他培南", "Imipenem", "Meropenem", "Ertapenem"});
            boolean inhibitorComboS = lookup.isAnySusceptible(INHIBITOR_COMBOS);
            if (ceph3OrAztreonamR && !carbapenemR && (carbapenemS || inhibitorComboS)) {
                alerts.add(buildAlert(reportId, organism, "ESBL",
                        "疑似产超广谱β-内酰胺酶(ESBL)：第三代头孢菌素/氨曲南耐药，碳青霉烯类或酶抑制剂复方敏感",
                        MicroAlert.LEVEL_WARNING));
            }
        }

        // ===== 3. 内置计算规则: MDR (对3类及以上抗生素耐药) =====
        int resistantClassCount = lookup.countResistantClasses();
        if (resistantClassCount >= 3) {
            alerts.add(buildAlert(reportId, organism, "MDR",
                    "检测到多重耐药(MDR)，对" + resistantClassCount + "类抗生素耐药",
                    MicroAlert.LEVEL_WARNING));
        }

        return alerts;
    }

    /** 单条可配置规则的匹配判定 */
    private boolean ruleMatches(MicroOrganism organism, ResistanceRule rule, SensitivityLookup lookup) {
        if (rule == null || rule.getStatus() == null || rule.getStatus() != 1) {
            return false;
        }
        // 细菌匹配: bacteriaCodes 为空=适用于所有细菌, 否则按「编码严格命中 OR 名称关键字别名命中」双通道匹配
        List<String> codes = CodeListUtil.parseCodes(rule.getBacteriaCodes());
        if (!organismMatchesByCodeOrAlias(organism, codes)) {
            return false;
        }
        List<String> abCodes = CodeListUtil.parseCodes(rule.getAntibioticCodes());
        if (abCodes.isEmpty()) {
            return false;
        }
        boolean anyResistant = !"EXACT".equalsIgnoreCase(rule.getMatchMode());
        String expect = rule.getResult();
        for (String code : abCodes) {
            if (anyResistant) {
                if (lookup.isResistant(code)) {
                    return true;
                }
            } else if (expect != null && !expect.isEmpty()) {
                if (lookup.hasResult(code, expect)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 细菌编码/别名双通道匹配。
     *
     * <p>判定顺序:
     * <ol>
     *   <li>codes 为空 → 全匹配(规则对所有细菌生效)</li>
     *   <li>organismCode 严格命中 codes 列表任一编码 → true</li>
     *   <li>organismName 包含 codes 列表中的任一值 (当作关键字) → true</li>
     *   <li>codes 任一值命中 BACTERIA_CODE_ALIAS 别名映射, 且 organism 的 code/name 命中对应别名列表 → true</li>
     * </ol>
     */
    private static boolean organismMatchesByCodeOrAlias(MicroOrganism organism, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return true;
        }
        if (organism == null) {
            return false;
        }
        String code = organism.getOrganismCode();
        String name = organism.getOrganismName();
        // 1) 编码严格命中
        if (code != null && CodeListUtil.matchesAny(code, codes)) {
            return true;
        }
        String nameLower = (name != null) ? name.toLowerCase() : "";
        for (String ruleCode : codes) {
            if (ruleCode == null || ruleCode.isEmpty()) continue;
            // 2) 名称包含规则编码关键字 (大小写不敏感)
            if (!nameLower.isEmpty() && nameLower.contains(ruleCode.toLowerCase())) {
                return true;
            }
            // 3) 别名表兜底: 若该编码存在已知别名列表, 检查 organism code/name 是否落在别名中
            List<String> aliases = BACTERIA_CODE_ALIAS.get(ruleCode);
            if (aliases == null || aliases.isEmpty()) continue;
            for (String alias : aliases) {
                if (alias == null) continue;
                if (code != null && code.equalsIgnoreCase(alias)) {
                    return true;
                }
                if (!nameLower.isEmpty() && nameLower.contains(alias.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(String name, String... keywords) {
        String lower = name.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** 根据可配置规则构建预警 */
    private MicroAlert buildAlert(Long reportId, MicroOrganism organism, ResistanceRule rule) {
        boolean critical = rule.getIsCritical() != null && rule.getIsCritical() == 1;
        String msg = (rule.getAlertMessage() != null && !rule.getAlertMessage().isEmpty())
                ? rule.getAlertMessage() : ("命中耐药规则: " + rule.getRuleName());
        return buildAlert(reportId, organism, rule.getRuleName(), msg,
                critical ? MicroAlert.LEVEL_CRITICAL : MicroAlert.LEVEL_WARNING);
    }

    private MicroAlert buildAlert(Long reportId, MicroOrganism organism, String alertType, String message, String level) {
        MicroAlert alert = new MicroAlert();
        alert.setMicroReportId(reportId);
        alert.setOrganismId(organism.getId());
        alert.setOrganismName(organism.getOrganismName());
        alert.setAlertType(alertType);
        alert.setAlertLevel(level);
        alert.setMessage(message);
        alert.setStatus(MicroAlert.STATUS_PENDING);
        alert.setCreateTime(LocalDateTime.now());
        log.info("专家/耐药规则触发预警: reportId={}, type={}, organism={}", reportId, alertType, organism.getOrganismName());
        return alert;
    }

    /**
     * 药敏结果查找辅助类: 支持按抗生素编码(精确)或名称(包含)匹配。
     */
    private static class SensitivityLookup {
        private final List<AntibioticSensitivity> sensitivities;

        SensitivityLookup(List<AntibioticSensitivity> sensitivities) {
            this.sensitivities = sensitivities;
        }

        boolean isResistant(String codeOrKeyword) {
            return matches(codeOrKeyword, "R");
        }

        boolean isSusceptible(String codeOrKeyword) {
            return matches(codeOrKeyword, "S");
        }

        boolean hasResult(String codeOrKeyword, String expectedResult) {
            return matches(codeOrKeyword, expectedResult);
        }

        boolean isAnyResistant(String... keywords) {
            if (keywords == null) return false;
            for (String kw : keywords) {
                if (isResistant(kw)) return true;
            }
            return false;
        }

        boolean isAnySusceptible(String... keywords) {
            if (keywords == null) return false;
            for (String kw : keywords) {
                if (isSusceptible(kw)) return true;
            }
            return false;
        }

        /** 按编码(相等)或名称(包含) + 期望结果匹配 */
        private boolean matches(String codeOrKeyword, String expectedResult) {
            if (codeOrKeyword == null || codeOrKeyword.isEmpty()) {
                return false;
            }
            String kw = codeOrKeyword.trim();
            String kwLower = kw.toLowerCase();
            return sensitivities.stream().anyMatch(s -> s != null && expectedResult.equals(s.getResult()) && (
                    (s.getAntibioticCode() != null && s.getAntibioticCode().equalsIgnoreCase(kwLower))
                            || (s.getAntibioticName() != null
                            && s.getAntibioticName().toLowerCase().contains(kwLower))
            ));
        }

        int countResistantClasses() {
            String[][] drugClasses = {
                {"青霉素", "阿莫西林", "哌拉西林", "penicillin", "ampicillin", "amoxicillin"},
                {"头孢", "cef", "cephalosporin"},
                {"碳青霉烯", "亚胺培南", "美罗培南", "carbapenem", "imipenem", "meropenem"},
                {"氨基糖苷", "庆大霉素", "阿米卡星", "妥布霉素", "aminoglycoside", "gentamicin", "amikacin"},
                {"喹诺酮", "环丙沙星", "左氧氟沙星", "诺氟沙星", "fluoroquinolone", "ciprofloxacin", "levofloxacin"},
                {"大环内酯", "红霉素", "阿奇霉素", "克拉霉素", "macrolide", "erythromycin", "azithromycin"},
                {"四环素", "多西环素", "米诺环素", "tetracycline", "doxycycline", "minocycline"},
                {"糖肽", "万古霉素", "替考拉宁", "glycopeptide", "vancomycin"},
                {"林可", "克林霉素", "林可霉素", "lincosamide", "clindamycin"},
                {"硝基咪唑", "甲硝唑", "替硝唑", "metronidazole", "tinidazole"}
            };
            int count = 0;
            for (String[] drugClass : drugClasses) {
                for (String kw : drugClass) {
                    if (isResistant(kw)) {
                        count++;
                        break;
                    }
                }
            }
            return count;
        }
    }
}