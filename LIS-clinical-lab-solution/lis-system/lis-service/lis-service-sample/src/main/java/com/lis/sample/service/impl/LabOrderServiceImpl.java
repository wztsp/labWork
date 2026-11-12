package com.lis.sample.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.api.dto.OrderDTO;
import com.lis.api.dto.OrderItemDTO;
import com.lis.api.dto.OrderQueryDTO;
import com.lis.api.vo.OrderVO;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.util.StringUtil;
import com.lis.sample.entity.LabOrder;
import com.lis.sample.entity.OrderItem;
import com.lis.sample.entity.Patient;
import com.lis.sample.entity.Sample;
import com.lis.sample.entity.Visit;
import com.lis.sample.mapper.LabOrderMapper;
import com.lis.sample.mapper.OrderItemMapper;
import com.lis.sample.mapper.PatientMapper;
import com.lis.sample.mapper.SampleDataMapper;
import com.lis.sample.mapper.VisitMapper;
import com.lis.sample.service.BarcodeService;
import com.lis.sample.service.LabOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 检验申请 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabOrderServiceImpl extends ServiceImpl<LabOrderMapper, LabOrder> implements LabOrderService {

    private final PatientMapper patientMapper;
    private final VisitMapper visitMapper;
    private final OrderItemMapper orderItemMapper;
    private final SampleDataMapper sampleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BarcodeService barcodeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LabOrder> receiveOrder(OrderDTO dto) {
        if (StringUtil.isEmpty(dto.getPatientName())) {
            throw new BusinessException("患者姓名不能为空");
        }
        // 1. 创建或获取患者
        Patient patient = null;
        if (dto.getPatientId() != null) {
            patient = patientMapper.selectById(dto.getPatientId());
        }
        if (patient == null) {
            patient = new Patient();
            patient.setName(dto.getPatientName());
            patient.setPatientNo(barcodeService.generateBarcode("PATIENT"));
            patient.setCreateTime(LocalDateTime.now());
            patient.setUpdateTime(LocalDateTime.now());
            patientMapper.insert(patient);
        }

        // 2. 创建或获取就诊
        Visit visit = null;
        if (dto.getVisitId() != null) {
            visit = visitMapper.selectById(dto.getVisitId());
        }
        if (visit == null) {
            visit = new Visit();
            visit.setPatientId(patient.getId());
            visit.setVisitNo(barcodeService.generateBarcode("VISIT"));
            // orderType: 门诊/住院/急诊 -> 1/2/3
            Integer visitTypeCode = 1;
            if (StringUtil.isNotEmpty(dto.getOrderType())) {
                if (dto.getOrderType().contains("住")) visitTypeCode = 2;
                else if (dto.getOrderType().contains("急")) visitTypeCode = 3;
                else visitTypeCode = 1;
            }
            visit.setVisitType(visitTypeCode);
            visit.setDepartment(dto.getApplyDept());
            visit.setVisitTime(LocalDateTime.now());
            visit.setCreateTime(LocalDateTime.now());
            visitMapper.insert(visit);
        }

        // orderType: 门诊/住院/急诊 -> 1/2/3
        Integer orderTypeCode = 1;
        if (StringUtil.isNotEmpty(dto.getOrderType())) {
            if (dto.getOrderType().contains("住")) orderTypeCode = 2;
            else if (dto.getOrderType().contains("急")) orderTypeCode = 3;
            else orderTypeCode = 1;
        }

        // 3. 拆分申请: 一个申请只能含一个检验项目, 多个项目拆分成多个独立申请
        List<OrderItemDTO> items = dto.getItems();
        List<LabOrder> createdOrders = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            // 无项目时也创建一个空申请 (兼容场景)
            LabOrder order = buildAndSaveOrder(dto, visit, patient, orderTypeCode, null);
            createdOrders.add(order);
        } else {
            for (OrderItemDTO itemDTO : items) {
                // 每个检验项目独立创建一个申请, 独立生成 orderNo
                LabOrder order = buildAndSaveOrder(dto, visit, patient, orderTypeCode, itemDTO.getHisApplyId());
                // 该申请只含这一个检验项目
                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setTestItemId(itemDTO.getTestItemId());
                item.setTestItemCode(itemDTO.getTestItemCode());
                item.setTestItemName(itemDTO.getTestItemName());
                item.setStatus(0);
                item.setFee(itemDTO.getFee());
                item.setCreateTime(LocalDateTime.now());
                orderItemMapper.insert(item);
                createdOrders.add(order);
            }
        }

        log.info("接收HIS检验申请成功, 拆分为{}个申请, patient={}, visitNo={}",
                createdOrders.size(), patient.getName(), visit.getVisitNo());
        return createdOrders;
    }

    /**
     * 构建并保存一个申请 (一申请一项目模型)
     */
    private LabOrder buildAndSaveOrder(OrderDTO dto, Visit visit, Patient patient, Integer orderTypeCode,
                                       String hisApplyId) {
        LabOrder order = new LabOrder();
        // 每个申请独立生成新 orderNo (不使用HIS的orderNo, 避免冲突)
        order.setOrderNo(barcodeService.generateBarcode("ORDER"));
        order.setVisitId(visit.getId());
        order.setPatientId(patient.getId());
        order.setOrderType(orderTypeCode);
        order.setStatus(1);
        order.setApplyDoctor(dto.getApplyDoctor());
        order.setApplyDept(dto.getApplyDept());
        order.setClinicalDiag(dto.getClinicalDiag());
        order.setPatientCondition(dto.getPatientCondition());
        order.setCollectionMethodId(dto.getCollectionMethodId());
        order.setCollectionMethodName(dto.getCollectionMethodName());
        order.setApplyTime(LocalDateTime.now());
        order.setReceiveTime(LocalDateTime.now());
        order.setHisSyncStatus(1);
        order.setHisApplyId(hisApplyId);
        order.setCreateTime(LocalDateTime.now());
        this.save(order);
        return order;
    }

    @Override
    public LabOrder getOrder(Long id) {
        return this.getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id, String reason) {
        LabOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("检验申请不存在: " + id);
        }
        if (order.getStatus() == 2) {
            throw new BusinessException("申请已取消, 不可重复取消");
        }
        order.setStatus(2);
        order.setCancelTime(LocalDateTime.now());
        if (StringUtil.isNotEmpty(reason)) {
            order.setClinicalDiag(order.getClinicalDiag() + " | 取消原因: " + reason);
        }
        this.updateById(order);

        // 取消申请项目
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, id);
        List<OrderItem> items = orderItemMapper.selectList(wrapper);
        for (OrderItem item : items) {
            item.setStatus(2);
            orderItemMapper.updateById(item);
        }
        log.info("检验申请已取消, id={}, reason={}", id, reason);
    }

    @Override
    public Page<OrderVO> queryPage(OrderQueryDTO query) {
        LambdaQueryWrapper<LabOrder> wrapper = new LambdaQueryWrapper<>();
        // 按就诊号查询: 先查Visit表获取visitId
        if (StringUtil.isNotEmpty(query.getVisitNo())) {
            LambdaQueryWrapper<Visit> visitWrapper = new LambdaQueryWrapper<>();
            visitWrapper.eq(Visit::getVisitNo, query.getVisitNo());
            Visit visit = visitMapper.selectOne(visitWrapper);
            if (visit != null) {
                wrapper.eq(LabOrder::getVisitId, visit.getId());
            } else {
                // 就诊号不存在, 返回空结果
                return new Page<>(query.getCurrent(), query.getSize(), 0);
            }
        }
        // 按患者姓名查询: 先查Patient表获取patientId列表
        if (StringUtil.isNotEmpty(query.getPatientName())) {
            LambdaQueryWrapper<Patient> patientWrapper = new LambdaQueryWrapper<>();
            patientWrapper.like(Patient::getName, query.getPatientName());
            List<Patient> patients = patientMapper.selectList(patientWrapper);
            if (patients.isEmpty()) {
                return new Page<>(query.getCurrent(), query.getSize(), 0);
            }
            List<Long> patientIds = patients.stream().map(Patient::getId).collect(Collectors.toList());
            wrapper.in(LabOrder::getPatientId, patientIds);
        }
        // 按申请单号查询
        if (StringUtil.isNotEmpty(query.getOrderNo())) {
            wrapper.eq(LabOrder::getOrderNo, query.getOrderNo());
        }
        if (query.getStatus() != null) {
            wrapper.eq(LabOrder::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(LabOrder::getCreateTime);

        Page<LabOrder> page = this.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        Page<OrderVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<OrderVO> voList = new ArrayList<>();
        for (LabOrder order : page.getRecords()) {
            voList.add(toOrderVO(order));
        }
        result.setRecords(voList);
        return result;
    }

    /**
     * LabOrder 转 OrderVO (附带就诊号/患者信息/项目列表/标本数)
     */
    private OrderVO toOrderVO(LabOrder order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setVisitId(order.getVisitId());
        vo.setPatientId(order.getPatientId());
        // orderType: 1门诊 2住院 3急诊 -> 中文
        if (order.getOrderType() != null) {
            String orderTypeText;
            switch (order.getOrderType()) {
                case 2: orderTypeText = "住院"; break;
                case 3: orderTypeText = "急诊"; break;
                default: orderTypeText = "门诊"; break;
            }
            vo.setOrderType(orderTypeText);
        }
        vo.setStatus(order.getStatus());
        vo.setApplyDoctor(order.getApplyDoctor());
        vo.setApplyDept(order.getApplyDept());
        vo.setClinicalDiag(order.getClinicalDiag());
        vo.setCollectionMethodId(order.getCollectionMethodId());
        vo.setCollectionMethodName(order.getCollectionMethodName());
        vo.setApplyTime(order.getApplyTime());
        vo.setCreateTime(order.getCreateTime());

        // 就诊信息
        if (order.getVisitId() != null) {
            Visit visit = visitMapper.selectById(order.getVisitId());
            if (visit != null) {
                vo.setVisitNo(visit.getVisitNo());
                // visitType: 1门诊 2住院 3急诊
                if (visit.getVisitType() != null) {
                    String visitTypeText;
                    switch (visit.getVisitType()) {
                        case 2: visitTypeText = "住院"; break;
                        case 3: visitTypeText = "急诊"; break;
                        default: visitTypeText = "门诊"; break;
                    }
                    vo.setVisitType(visitTypeText);
                }
            }
        }
        // 患者信息
        if (order.getPatientId() != null) {
            Patient patient = patientMapper.selectById(order.getPatientId());
            if (patient != null) {
                vo.setPatientName(patient.getName());
                vo.setPatientGender(patient.getGender() != null
                        ? (patient.getGender() == 1 ? "男" : patient.getGender() == 2 ? "女" : "未知") : "未知");
                vo.setPatientAge(patient.getAge() != null ? patient.getAge() + "岁" : "");
            }
        }
        // 项目列表
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);
        List<OrderVO.OrderItemVO> itemVOs = items.stream().map(i ->
                OrderVO.OrderItemVO.builder()
                        .id(i.getId())
                        .orderId(i.getOrderId())
                        .testItemId(i.getTestItemId())
                        .testItemCode(i.getTestItemCode())
                        .testItemName(i.getTestItemName())
                        .status(i.getStatus())
                        .fee(i.getFee())
                        .build()
        ).collect(Collectors.toList());
        vo.setItems(itemVOs);

        // 统计已生成的标本数
        LambdaQueryWrapper<Sample> sampleWrapper = new LambdaQueryWrapper<>();
        sampleWrapper.eq(Sample::getOrderId, order.getId());
        Long sampleCount = sampleMapper.selectCount(sampleWrapper);
        vo.setSampleCount(sampleCount != null ? sampleCount.intValue() : 0);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupGarbage() {
        // 1. 查找lis_patient中name包含???的患者id
        List<Long> badPatientIds = jdbcTemplate.queryForList(
                "SELECT id FROM lis_patient WHERE name LIKE '%?%'", Long.class);
        // 2. 查找lis_lab_order中apply_dept/apply_doctor/clinical_diag含???或patient_id在上面的申请id
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id FROM lis_lab_order WHERE ");
        sql.append("apply_dept LIKE '%?%' OR apply_doctor LIKE '%?%' OR clinical_diag LIKE '%?%' ");
        if (!badPatientIds.isEmpty()) {
            sql.append("OR patient_id IN (");
            for (int i = 0; i < badPatientIds.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
                args.add(badPatientIds.get(i));
            }
            sql.append(')');
        }
        List<Long> badOrderIds = jdbcTemplate.queryForList(sql.toString(), Long.class, args.toArray());
        if (badOrderIds.isEmpty()) {
            log.info("cleanupGarbage: 未发现乱码申请, 直接返回");
            return 0;
        }
        int orderCount = badOrderIds.size();
        // 批量构造id参数: DELETE lis_order_item, lis_sample, lis_lab_order
        String idPlaceholders = badOrderIds.stream().map(i -> "?").collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM lis_order_item WHERE order_id IN (" + idPlaceholders + ")", badOrderIds.toArray());
        jdbcTemplate.update("DELETE FROM lis_sample WHERE order_id IN (" + idPlaceholders + ")", badOrderIds.toArray());
        // 关联的visit id
        List<Long> badVisitIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT visit_id FROM lis_lab_order WHERE id IN (" + idPlaceholders + ")", Long.class, badOrderIds.toArray());
        jdbcTemplate.update("DELETE FROM lis_lab_order WHERE id IN (" + idPlaceholders + ")", badOrderIds.toArray());
        // 清理无引用的visit和patient
        if (!badVisitIds.isEmpty()) {
            String vp = badVisitIds.stream().map(i -> "?").collect(Collectors.joining(","));
            jdbcTemplate.update("DELETE FROM lis_visit WHERE id IN (" + vp + ")", badVisitIds.toArray());
        }
        if (!badPatientIds.isEmpty()) {
            String pp = badPatientIds.stream().map(i -> "?").collect(Collectors.joining(","));
            jdbcTemplate.update("DELETE FROM lis_patient WHERE id IN (" + pp + ")", badPatientIds.toArray());
        }
        log.info("cleanupGarbage: 删除申请={}, 就诊={}, 患者={}", orderCount, badVisitIds.size(), badPatientIds.size());
        return orderCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCollectionMethod(Long orderId, Long collectionMethodId, String collectionMethodName) {
        LabOrder order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException("检验申请不存在: " + orderId);
        }
        // 使用 LambdaUpdateWrapper 显式设置字段, 即使值为 null 也需更新
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LabOrder> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.eq(LabOrder::getId, orderId)
                .set(LabOrder::getCollectionMethodId, collectionMethodId)
                .set(LabOrder::getCollectionMethodName, collectionMethodName);
        this.update(wrapper);
        log.info("更新申请采集方式: orderId={}, methodId={}, methodName={}", orderId, collectionMethodId, collectionMethodName);
    }
}
