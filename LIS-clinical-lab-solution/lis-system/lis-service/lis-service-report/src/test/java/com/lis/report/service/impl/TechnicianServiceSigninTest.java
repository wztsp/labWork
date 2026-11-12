package com.lis.report.service.impl;

import com.lis.common.core.exception.BusinessException;
import com.lis.report.dto.BatchOpResult;
import com.lis.report.entity.StationSignin;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.mapper.TechnicianMapper;
import com.lis.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 技师工作站：补打条码 + 工作站签到/签退 单元测试（B-2 回归）。
 *
 * <p>验证 B-2 真实落库：batchReprintBarcode→insertBarcodePrintLog、instrumentSignin/autoAuditSignin
 * 先 closeOpenSignin 再 insertSignin、signout 仅 closeOpenSignin、空必填项抛 BusinessException、
 * 返回真实影响行数（BatchOpResult）。直接 new 服务注入 mock mapper。</p>
 */
@ExtendWith(MockitoExtension.class)
class TechnicianServiceSigninTest {

    @Mock
    private TechnicianMapper technicianMapper;
    @Mock
    private ResultMapper resultMapper;
    @Mock
    private ReportService reportService;

    private TechnicianServiceImpl service() {
        return new TechnicianServiceImpl(technicianMapper, resultMapper, reportService);
    }

    @Test
    void batchReprintBarcode_emptySampleIds_returnsZeroTotalZeroSuccess() {
        Map<String, Object> params = new HashMap<>();
        BatchOpResult r = service().batchReprintBarcode(params);
        assertEquals(0, r.getTotal());
        assertEquals(0, r.getSuccess());
    }

    @Test
    void batchReprintBarcode_insertsPrintLogForEachSample() {
        Map<String, Object> params = new HashMap<>();
        params.put("sampleIds", List.of(1L, 2L));
        params.put("copies", 2);

        Map<String, Object> detail = new HashMap<>();
        detail.put("sample_barcode", "BC1");
        when(technicianMapper.selectSampleDetail(any())).thenReturn(detail);
        when(technicianMapper.insertBarcodePrintLog(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        BatchOpResult r = service().batchReprintBarcode(params);
        assertEquals(2, r.getTotal());
        assertEquals(2, r.getSuccess());
        verify(technicianMapper, org.mockito.Mockito.times(2))
                .insertBarcodePrintLog(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void batchReprintBarcode_sampleNotFound_skipped() {
        Map<String, Object> params = new HashMap<>();
        params.put("sampleIds", List.of(1L));
        when(technicianMapper.selectSampleDetail(any())).thenReturn(null);

        BatchOpResult r = service().batchReprintBarcode(params);
        assertEquals(1, r.getTotal());
        assertEquals(0, r.getSuccess());
        assertTrue(r.getMessages().stream().anyMatch(m -> m.contains("不存在")));
    }

    @Test
    void batchReprintBarcode_defaultCopiesToOne() {
        Map<String, Object> params = new HashMap<>();
        params.put("sampleIds", List.of(1L));

        Map<String, Object> detail = new HashMap<>();
        detail.put("sample_barcode", "BC1");
        when(technicianMapper.selectSampleDetail(any())).thenReturn(detail);
        when(technicianMapper.insertBarcodePrintLog(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        service().batchReprintBarcode(params);

        ArgumentCaptor<Integer> copiesCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(technicianMapper).insertBarcodePrintLog(eq(1L), any(), copiesCaptor.capture(),
                any(), any(), any(), any(), any());
        assertEquals(1, copiesCaptor.getValue());
    }

    @Test
    void instrumentSignin_nullInstrumentId_throwsBusinessException() {
        Map<String, Object> params = new HashMap<>();
        assertThrows(BusinessException.class, () -> service().instrumentSignin(params));
    }

    @Test
    void instrumentSignin_signin_insertsAndClosesOld() {
        Map<String, Object> params = new HashMap<>();
        params.put("instrumentId", 10L);
        when(technicianMapper.closeOpenSignin(any(), any(), any())).thenReturn(0);
        when(technicianMapper.insertSignin(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        BatchOpResult r = service().instrumentSignin(params);
        assertEquals(1, r.getTotal());
        assertEquals(1, r.getSuccess());
        verify(technicianMapper).closeOpenSignin(eq(StationSignin.TYPE_INSTRUMENT), eq(10L), any(LocalDateTime.class));
        verify(technicianMapper).insertSignin(eq(StationSignin.TYPE_INSTRUMENT), eq(10L), any(), any(), any(), any(), any());
    }

    @Test
    void instrumentSignin_signout_onlyCloses() {
        Map<String, Object> params = new HashMap<>();
        params.put("instrumentId", 10L);
        params.put("action", "signout");
        when(technicianMapper.closeOpenSignin(any(), any(), any())).thenReturn(1);

        BatchOpResult r = service().instrumentSignin(params);
        assertEquals(1, r.getTotal());
        assertEquals(1, r.getSuccess());
        verify(technicianMapper).closeOpenSignin(eq(StationSignin.TYPE_INSTRUMENT), eq(10L), any(LocalDateTime.class));
        verify(technicianMapper, org.mockito.Mockito.never())
                .insertSignin(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void autoAuditSignin_nullOperatorId_throwsBusinessException() {
        Map<String, Object> params = new HashMap<>();
        assertThrows(BusinessException.class, () -> service().autoAuditSignin(params));
    }

    @Test
    void autoAuditSignin_signin_insertsWithNullInstrument() {
        Map<String, Object> params = new HashMap<>();
        params.put("operatorId", 5L);
        when(technicianMapper.closeOpenSignin(any(), any(), any())).thenReturn(0);
        when(technicianMapper.insertSignin(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        BatchOpResult r = service().autoAuditSignin(params);
        assertEquals(1, r.getTotal());
        assertEquals(1, r.getSuccess());
        verify(technicianMapper).insertSignin(eq(StationSignin.TYPE_AUTO_AUDIT), isNull(), eq(5L),
                any(), any(), any(), any());
    }
}
