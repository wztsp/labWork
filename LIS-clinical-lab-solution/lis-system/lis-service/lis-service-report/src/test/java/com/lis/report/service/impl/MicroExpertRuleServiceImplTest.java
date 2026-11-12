package com.lis.report.service.impl;

import com.lis.micro.service.ResistanceRuleService;
import com.lis.report.entity.AntibioticSensitivity;
import com.lis.report.entity.MicroAlert;
import com.lis.report.entity.MicroOrganism;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微生物耐药规则引擎单元测试（B-1 回归）。
 *
 * <p>重点验证 ESBL 判定：三代头孢/氨曲南耐药 且 非碳青霉烯耐药 且 (碳青霉烯敏感 或 酶抑制剂复方敏感)，
 * 三个条件全 {@code &&} 串联，与 CRE 互斥（缺陷 B-1 修复点）。直接 {@code new} 服务调用 evaluate。</p>
 */
class MicroExpertRuleServiceImplTest {

    private final MicroExpertRuleServiceImpl service =
            new MicroExpertRuleServiceImpl(Mockito.mock(ResistanceRuleService.class));

    private static MicroOrganism organism(String name) {
        MicroOrganism o = new MicroOrganism();
        o.setId(1L);
        o.setOrganismName(name);
        return o;
    }

    private static AntibioticSensitivity sens(String name, String result) {
        AntibioticSensitivity s = new AntibioticSensitivity();
        s.setAntibioticName(name);
        s.setResult(result);
        return s;
    }

    private static boolean hasType(List<MicroAlert> alerts, String type) {
        return alerts.stream().anyMatch(a -> type.equals(a.getAlertType()));
    }

    @Test
    void esbl_reported_whenCeph3RAndCarbapenemSensitive() {
        List<AntibioticSensitivity> list = new ArrayList<>();
        list.add(sens("头孢他啶", "R"));
        list.add(sens("亚胺培南", "S"));

        List<MicroAlert> alerts = service.evaluate(100L, organism("大肠埃希菌"), list);
        assertTrue(hasType(alerts, "ESBL"));
        assertFalse(hasType(alerts, "CRE"));
    }

    @Test
    void esbl_notReported_whenCarbapenemResistant() {
        // 碳青霉烯耐药 → ESBL 表型被掩盖，只报 CRE（与 CRE 互斥）
        List<AntibioticSensitivity> list = new ArrayList<>();
        list.add(sens("头孢他啶", "R"));
        list.add(sens("亚胺培南", "R"));

        List<MicroAlert> alerts = service.evaluate(100L, organism("大肠埃希菌"), list);
        assertFalse(hasType(alerts, "ESBL"));
        assertTrue(hasType(alerts, "CRE"));
    }

    @Test
    void esbl_reported_viaInhibitorComboSusceptible() {
        // 无碳青霉烯药敏，但酶抑制剂复方敏感 → 仍报 ESBL
        List<AntibioticSensitivity> list = new ArrayList<>();
        list.add(sens("头孢曲松", "R"));
        list.add(sens("头孢哌酮舒巴坦", "S"));

        List<MicroAlert> alerts = service.evaluate(100L, organism("肺炎克雷伯菌"), list);
        assertTrue(hasType(alerts, "ESBL"));
        assertFalse(hasType(alerts, "CRE"));
    }

    @Test
    void mdr_reported_whenThreeOrMoreClassesResistant() {
        List<AntibioticSensitivity> list = new ArrayList<>();
        list.add(sens("青霉素", "R"));
        list.add(sens("头孢他啶", "R"));
        list.add(sens("亚胺培南", "R"));
        list.add(sens("庆大霉素", "R"));
        list.add(sens("环丙沙星", "R"));

        List<MicroAlert> alerts = service.evaluate(100L, organism("大肠埃希菌"), list);
        assertTrue(hasType(alerts, "MDR"));
        assertTrue(hasType(alerts, "CRE"));
    }

    @Test
    void mrsa_reported_forStaphylococcusAureusOxacillinR() {
        List<AntibioticSensitivity> list = new ArrayList<>();
        list.add(sens("苯唑西林", "R"));

        List<MicroAlert> alerts = service.evaluate(100L, organism("金黄色葡萄球菌"), list);
        assertTrue(hasType(alerts, "MRSA"));
    }

    @Test
    void vre_reported_forEnterococcusVancomycinR() {
        List<AntibioticSensitivity> list = new ArrayList<>();
        list.add(sens("万古霉素", "R"));

        List<MicroAlert> alerts = service.evaluate(100L, organism("肠球菌"), list);
        assertTrue(hasType(alerts, "VRE"));
    }

    @Test
    void crab_reported_forAcinetobacterImipenemR() {
        List<AntibioticSensitivity> list = new ArrayList<>();
        list.add(sens("亚胺培南", "R"));

        List<MicroAlert> alerts = service.evaluate(100L, organism("鲍曼不动杆菌"), list);
        assertTrue(hasType(alerts, "CRAB"));
    }

    @Test
    void criticalValue_reported_forColistinResistant() {
        List<AntibioticSensitivity> list = new ArrayList<>();
        list.add(sens("多粘菌素", "R"));

        List<MicroAlert> alerts = service.evaluate(100L, organism("大肠埃希菌"), list);
        assertTrue(hasType(alerts, "CRITICAL_VALUE"));
        assertEquals(MicroAlert.LEVEL_CRITICAL, alerts.get(0).getAlertLevel());
    }

    @Test
    void noAlert_whenEmptyInputs() {
        assertTrue(service.evaluate(100L, null, List.of()).isEmpty());
        assertTrue(service.evaluate(100L, organism("大肠埃希菌"), List.of()).isEmpty());
    }

    @Test
    void multipleAlerts_allDistinctTypes() {
        List<AntibioticSensitivity> list = new ArrayList<>();
        list.add(sens("头孢他啶", "R"));
        list.add(sens("亚胺培南", "S"));
        list.add(sens("多粘菌素", "R"));
        list.add(sens("青霉素", "R"));
        list.add(sens("庆大霉素", "R"));

        List<MicroAlert> alerts = service.evaluate(100L, organism("大肠埃希菌"), list);
        assertTrue(hasType(alerts, "ESBL"));
        assertTrue(hasType(alerts, "CRITICAL_VALUE"));
        assertTrue(hasType(alerts, "MDR"));
    }
}
