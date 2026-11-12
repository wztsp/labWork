package com.lis.sample.service.impl;

import com.lis.api.vo.TubeGroupVO;
import com.lis.sample.mapper.SampleInstrumentItemMapMapper;
import com.lis.sample.mapper.OrderItemMapper;
import com.lis.sample.mapper.SampleOrderItemMapper;
import com.lis.sample.service.BarcodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SampleServiceImpl#calculateTubeGroups(Long)} 合管计算单元测试。
 *
 * <p>该方法通过 {@code jdbcTemplate.queryForList(sql, visitId)} 单次取数后在内存分组,
 * 故只需 mock {@link JdbcTemplate} 即可完整覆盖分组算法, 无需数据库。</p>
 *
 * <p>合管规则: <b>相同试管 + 相同标本类型 = 一根管 (可跨申请合并)</b>;
 * 未绑定试管的检验项目<b>各自独立一管</b>。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SampleServiceTubeGroupTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private SampleInstrumentItemMapMapper instrumentItemMapMapper;
    @Mock
    private SampleOrderItemMapper sampleOrderItemMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private BarcodeService barcodeService;

    private SampleServiceImpl service;

    private static final Long VISIT_ID = 3001L;

    @BeforeEach
    void setUp() {
        service = new SampleServiceImpl(rabbitTemplate, orderItemMapper, instrumentItemMapMapper,
                sampleOrderItemMapper, jdbcTemplate, barcodeService);
    }

    /** 构造一行查询结果 */
    private static Map<String, Object> row(Long orderItemId, Long orderId, Long testItemId,
                                           String itemCode, String itemName,
                                           String specimenType, Long tubeId,
                                           String tubeName, String tubeColor) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("order_item_id", orderItemId);
        r.put("order_id", orderId);
        r.put("test_item_id", testItemId);
        r.put("oi_item_name", itemName);
        r.put("item_code", itemCode);
        r.put("ti_item_name", itemName);
        r.put("specimen_type", specimenType);
        r.put("tube_id", tubeId);
        r.put("tube_name", tubeName);
        r.put("tube_color", tubeColor);
        return r;
    }

    private void givenRows(List<Map<String, Object>> rows) {
        when(jdbcTemplate.queryForList(anyString(), eq(VISIT_ID))).thenReturn(rows);
    }

    // ==================== 合管 ====================

    @Test
    @DisplayName("calculateTubeGroups_sameTubeAndSpecimenType_mergedIntoOneGroup")
    void calculateTubeGroups_sameTubeAndSpecimenType_mergedIntoOneGroup() {
        givenRows(Arrays.asList(
                row(1L, 100L, 11L, "ALT", "谷丙转氨酶", "血清", 5L, "黄色分离胶管", "#FFD700"),
                row(2L, 100L, 12L, "AST", "谷草转氨酶", "血清", 5L, "黄色分离胶管", "#FFD700")));

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertAll(
                () -> assertEquals(1, groups.size(), "同管同标本类型必须合并为一管"),
                () -> assertEquals(2, groups.get(0).getItemCount()),
                () -> assertEquals(2, groups.get(0).getTestItems().size()),
                () -> assertEquals(5L, groups.get(0).getTubeId()),
                () -> assertEquals("血清", groups.get(0).getSampleType()),
                () -> assertEquals("黄色分离胶管", groups.get(0).getContainerType()),
                () -> assertEquals(1, groups.get(0).getGroupIndex())
        );
    }

    @Test
    @DisplayName("calculateTubeGroups_sameTubeDifferentSpecimenType_splitIntoTwoGroups")
    void calculateTubeGroups_sameTubeDifferentSpecimenType_splitIntoTwoGroups() {
        givenRows(Arrays.asList(
                row(1L, 100L, 11L, "ALT", "谷丙转氨酶", "血清", 5L, "黄色分离胶管", "#FFD700"),
                row(2L, 100L, 12L, "K", "血钾", "全血", 5L, "黄色分离胶管", "#FFD700")));

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertAll(
                () -> assertEquals(2, groups.size(), "试管相同但标本类型不同不可合管"),
                () -> assertEquals(1, groups.get(0).getGroupIndex()),
                () -> assertEquals(2, groups.get(1).getGroupIndex())
        );
    }

    @Test
    @DisplayName("calculateTubeGroups_crossOrderSameTube_mergedAndKeepsOrderId")
    void calculateTubeGroups_crossOrderSameTube_mergedAndKeepsOrderId() {
        givenRows(Arrays.asList(
                row(1L, 100L, 11L, "ALT", "谷丙转氨酶", "血清", 5L, "黄色分离胶管", "#FFD700"),
                row(2L, 200L, 12L, "AST", "谷草转氨酶", "血清", 5L, "黄色分离胶管", "#FFD700")));

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertEquals(1, groups.size(), "跨申请但同管同标本类型应合并");
        List<TubeGroupVO.TestItemInfo> items = groups.get(0).getTestItems();
        assertAll(
                () -> assertEquals(2, items.size()),
                () -> assertEquals(100L, items.get(0).getOrderId(), "合管后仍需保留各自所属申请"),
                () -> assertEquals(200L, items.get(1).getOrderId()),
                () -> assertEquals(1L, items.get(0).getOrderItemId()),
                () -> assertEquals(2L, items.get(1).getOrderItemId())
        );
    }

    @Test
    @DisplayName("calculateTubeGroups_unboundTube_eachItemFormsOwnGroup")
    void calculateTubeGroups_unboundTube_eachItemFormsOwnGroup() {
        givenRows(Arrays.asList(
                row(1L, 100L, 11L, "X1", "未绑管项目1", "血清", null, null, null),
                row(2L, 100L, 12L, "X2", "未绑管项目2", "血清", null, null, null)));

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertAll(
                () -> assertEquals(2, groups.size(), "未绑管的项目必须各自独立成组"),
                () -> assertNull(groups.get(0).getTubeId()),
                () -> assertEquals("未绑定试管", groups.get(0).getTubeName()),
                () -> assertEquals("未绑定试管", groups.get(0).getContainerType()),
                () -> assertEquals(1, groups.get(0).getItemCount()),
                () -> assertEquals(1, groups.get(1).getItemCount())
        );
    }

    @Test
    @DisplayName("calculateTubeGroups_sameUnboundTestItemTwice_groupedByTestItemId")
    void calculateTubeGroups_sameUnboundTestItemTwice_groupedByTestItemId() {
        // 未绑管分组 key 为 none_{testItemId}: 同一检验项目跨申请仍归一组
        givenRows(Arrays.asList(
                row(1L, 100L, 11L, "X1", "未绑管项目", "血清", null, null, null),
                row(2L, 200L, 11L, "X1", "未绑管项目", "血清", null, null, null)));

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).getItemCount());
    }

    @Test
    @DisplayName("calculateTubeGroups_mixedTubeAndUnbound_indexIncrementsFromOne")
    void calculateTubeGroups_mixedTubeAndUnbound_indexIncrementsFromOne() {
        givenRows(Arrays.asList(
                row(1L, 100L, 11L, "ALT", "谷丙转氨酶", "血清", 5L, "黄色分离胶管", "#FFD700"),
                row(2L, 100L, 12L, "CBC", "血常规", "全血", 6L, "紫色EDTA管", "#800080"),
                row(3L, 100L, 13L, "X1", "未绑管项目", "血清", null, null, null),
                row(4L, 100L, 14L, "AST", "谷草转氨酶", "血清", 5L, "黄色分离胶管", "#FFD700")));

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertAll(
                () -> assertEquals(3, groups.size()),
                () -> assertEquals(1, groups.get(0).getGroupIndex()),
                () -> assertEquals(2, groups.get(1).getGroupIndex()),
                () -> assertEquals(3, groups.get(2).getGroupIndex()),
                () -> assertEquals(2, groups.get(0).getItemCount(), "第 1 管应合并 ALT + AST"),
                () -> assertEquals(1, groups.get(1).getItemCount()),
                () -> assertEquals(1, groups.get(2).getItemCount())
        );
    }

    @Test
    @DisplayName("calculateTubeGroups_emptyQueryResult_returnsEmptyList")
    void calculateTubeGroups_emptyQueryResult_returnsEmptyList() {
        givenRows(Collections.emptyList());

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertTrue(groups.isEmpty(), "无待合管项目时应返回空列表而非 null");
    }

    @Test
    @DisplayName("calculateTubeGroups_nullSpecimenTypeAndTubeName_useDefaults")
    void calculateTubeGroups_nullSpecimenTypeAndTubeName_useDefaults() {
        givenRows(Collections.singletonList(
                row(1L, 100L, 11L, "X1", "缺配置项目", null, 5L, null, null)));

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertAll(
                () -> assertEquals(1, groups.size()),
                () -> assertEquals("未知", groups.get(0).getSampleType(), "标本类型缺失应降级为'未知'"),
                () -> assertEquals("未绑定试管", groups.get(0).getTubeName()),
                () -> assertEquals("", groups.get(0).getTubeColor())
        );
    }

    @Test
    @DisplayName("calculateTubeGroups_integerIdsFromJdbc_convertedToLongSafely")
    void calculateTubeGroups_integerIdsFromJdbc_convertedToLongSafely() {
        // JDBC 驱动可能返回 Integer, 分组逻辑需按 Number 统一转 Long
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("order_item_id", 1);
        r.put("order_id", 100);
        r.put("test_item_id", 11);
        r.put("oi_item_name", "谷丙转氨酶");
        r.put("item_code", "ALT");
        r.put("ti_item_name", "谷丙转氨酶");
        r.put("specimen_type", "血清");
        r.put("tube_id", 5);
        r.put("tube_name", "黄色分离胶管");
        r.put("tube_color", "#FFD700");
        givenRows(Collections.singletonList(r));

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertAll(
                () -> assertEquals(1, groups.size()),
                () -> assertEquals(5L, groups.get(0).getTubeId()),
                () -> assertEquals(11L, groups.get(0).getTestItems().get(0).getTestItemId()),
                () -> assertEquals(100L, groups.get(0).getTestItems().get(0).getOrderId())
        );
    }

    @Test
    @DisplayName("calculateTubeGroups_singleQuery_noNPlusOne")
    void calculateTubeGroups_singleQuery_noNPlusOne() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rows.add(row((long) i, 100L, (long) (10 + i), "C" + i, "项目" + i, "血清", 5L, "黄色分离胶管", "#FFD700"));
        }
        givenRows(rows);

        service.calculateTubeGroups(VISIT_ID);

        verify(jdbcTemplate, times(1)).queryForList(anyString(), eq(VISIT_ID));
    }

    @Test
    @DisplayName("calculateTubeGroups_itemNameFallsBackToOrderItemName")
    void calculateTubeGroups_itemNameFallsBackToOrderItemName() {
        Map<String, Object> r = row(1L, 100L, 11L, "ALT", "谷丙转氨酶", "血清", 5L, "黄色分离胶管", "#FFD700");
        r.put("ti_item_name", null);
        r.put("oi_item_name", "申请单项目名");
        givenRows(Collections.singletonList(r));

        List<TubeGroupVO> groups = service.calculateTubeGroups(VISIT_ID);

        assertEquals("申请单项目名", groups.get(0).getTestItems().get(0).getItemName(),
                "检验项目表无名称时应回退取申请项目名称");
    }
}
