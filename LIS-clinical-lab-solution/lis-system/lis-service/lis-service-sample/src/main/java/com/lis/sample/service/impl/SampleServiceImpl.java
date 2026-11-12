package com.lis.sample.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.api.dto.SampleDTO;
import com.lis.api.dto.SampleQueryDTO;
import com.lis.api.dto.SampleReceiveDTO;
import com.lis.api.dto.SampleCollectDTO;
import com.lis.api.dto.SampleRejectDTO;
import com.lis.api.dto.BatchBarcodeDTO;
import com.lis.api.vo.SampleVO;
import com.lis.api.vo.TubeGroupVO;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.util.StringUtil;
import com.lis.sample.entity.OrderItem;
import com.lis.sample.entity.Sample;
import com.lis.sample.entity.SampleOrderItem;
import com.lis.sample.mapper.SampleInstrumentItemMapMapper;
import com.lis.sample.mapper.OrderItemMapper;
import com.lis.sample.mapper.SampleDataMapper;
import com.lis.sample.mapper.SampleOrderItemMapper;
import com.lis.sample.service.BarcodeService;
import com.lis.sample.service.SampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 标本 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleServiceImpl extends ServiceImpl<SampleDataMapper, Sample> implements SampleService {

    private final RabbitTemplate rabbitTemplate;
    private final OrderItemMapper orderItemMapper;
    private final SampleInstrumentItemMapMapper instrumentItemMapMapper;
    private final SampleOrderItemMapper sampleOrderItemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BarcodeService barcodeService;

    /** 异步发送MQ的线程池 (MQ不可用时不会阻塞主流程) */
    private final Executor mqExecutor = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mq-async-sender");
        t.setDaemon(true);
        return t;
    });

    /** 异步发送MQ消息 (不阻塞业务流程, MQ不可用时仅记录日志) */
    private void sendMqAsync(String routingKey, Object payload) {
        CompletableFuture.runAsync(() -> {
            try {
                rabbitTemplate.convertAndSend(routingKey, payload);
            } catch (Exception e) {
                log.debug("MQ发送失败(routingKey={}),忽略: {}", routingKey, e.getMessage());
            }
        }, mqExecutor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleVO createSample(SampleDTO dto) {
        Sample sample = new Sample();
        BeanUtils.copyProperties(dto, sample);
        // 生成条码: 按统一规则配置生成 (默认SAMPLE规则)
        String barcode = barcodeService.generateBarcode("SAMPLE");
        sample.setSampleBarcode(barcode);
        sample.setStatus(Sample.STATUS_APPLIED);
        // 急诊标记: 从申请单的 visit_type 判断 (visit_type==3 为急诊)
        if (isEmergencyOrder(sample.getOrderId())) {
            sample.setIsUrgent(1);
        } else if (sample.getIsUrgent() == null) {
            sample.setIsUrgent(0);
        }
        sample.setCreateTime(LocalDateTime.now());
        sample.setUpdateTime(LocalDateTime.now());
        this.save(sample);

        // 发MQ消息 sample.created (MQ不可用时不阻断主流程)
        sendMqAsync("sample.created", sample);
        log.info("标本创建成功, 发送 sample.created 消息, barcode={}", barcode);

        return toVO(sample);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleVO receiveSample(SampleReceiveDTO dto) {
        if (StringUtil.isEmpty(dto.getSampleBarcode())) {
            throw new BusinessException("标本条码不能为空");
        }
        if (dto.getInstrumentId() == null) {
            throw new BusinessException("核收时必须选择仪器");
        }
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sample::getSampleBarcode, dto.getSampleBarcode());
        Sample sample = this.getOne(wrapper);
        if (sample == null) {
            throw new BusinessException("标本不存在: " + dto.getSampleBarcode());
        }
        if (sample.getStatus() != null && sample.getStatus() >= Sample.STATUS_RECEIVED) {
            throw new BusinessException("标本已核收, 不可重复核收");
        }
        // a. 校验标本状态必须为2(已送检)或3(已签收)
        Integer curStatus = sample.getStatus();
        if (curStatus == null || (curStatus != Sample.STATUS_DELIVERED && curStatus != Sample.STATUS_SIGNED)) {
            throw new BusinessException("标本状态必须为已送检(2)或已签收(3)才能核收, 当前状态: "
                    + (curStatus != null ? Sample.statusText(curStatus) : "未知"));
        }
        // b. 校验患者信息是否存在 (patient_id 不为空)
        if (sample.getPatientId() == null) {
            throw new BusinessException("标本缺少患者信息(patient_id为空), 不可核收");
        }

        // 1. 校验仪器是否存在且启用
        if (instrumentItemMapMapper.countEnabledById(dto.getInstrumentId()) == 0) {
            throw new BusinessException("所选仪器不存在或已停用, instrumentId=" + dto.getInstrumentId());
        }

        // 2. 校验仪器是否能检测该条码对应的所有检验项目
        List<OrderItem> orderItems = listOrderItems(sample.getOrderId());
        if (!orderItems.isEmpty()) {
            List<Long> supportedItemIds = instrumentItemMapMapper
                    .selectTestItemIdsByInstrumentId(dto.getInstrumentId());
            Set<Long> supportedSet = new HashSet<>(supportedItemIds);
            List<String> unsupportedNames = orderItems.stream()
                    .filter(oi -> !supportedSet.contains(oi.getTestItemId()))
                    .map(OrderItem::getTestItemName)
                    .collect(Collectors.toList());
            if (!unsupportedNames.isEmpty()) {
                String instrumentName = instrumentItemMapMapper
                        .selectInstrumentNameById(dto.getInstrumentId());
                throw new BusinessException("仪器 [" + (instrumentName == null ? dto.getInstrumentId() : instrumentName)
                        + "] 不支持检测以下检验项目: " + String.join("、", unsupportedNames)
                        + ", 请选择其他仪器或配置仪器项目映射");
            }
        }

        // c. 校验标本类型是否被仪器支持
        //    优先使用 lis_instrument_specimen_map (仪器级标本类型映射, 即仪器字典"标本类型"分页配置)
        //    若该表未配置任何标本类型, 回退到 lis_instrument_item_map.specimen_type (项目级配置)
        //    空字符串视为未配置(等同 NULL)
        if (StringUtil.isNotEmpty(sample.getSampleType())) {
            String sampleType = sample.getSampleType().trim();
            // 1) 优先查仪器级标本类型映射
            boolean hasSpecimenMap = false;
            boolean specimenMatched = false;
            try {
                List<Map<String, Object>> spRows = jdbcTemplate.queryForList(
                        "SELECT specimen_type_code, specimen_type_name FROM lis_instrument_specimen_map WHERE instrument_id = ?",
                        dto.getInstrumentId());
                if (!spRows.isEmpty()) {
                    hasSpecimenMap = true;
                    for (Map<String, Object> row : spRows) {
                        Object code = row.get("specimen_type_code");
                        Object name = row.get("specimen_type_name");
                        if (code != null && code.toString().trim().equalsIgnoreCase(sampleType)) {
                            specimenMatched = true; break;
                        }
                        if (name != null && name.toString().trim().equalsIgnoreCase(sampleType)) {
                            specimenMatched = true; break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("查询仪器标本类型映射失败, instrumentId={}: {}", dto.getInstrumentId(), e.getMessage());
            }
            if (hasSpecimenMap) {
                if (!specimenMatched) {
                    String instrumentName = instrumentItemMapMapper.selectInstrumentNameById(dto.getInstrumentId());
                    throw new BusinessException("仪器 [" + (instrumentName == null ? dto.getInstrumentId() : instrumentName)
                            + "] 不支持以标本类型[" + sampleType + "]检测, 请在仪器字典的\"标本类型\"分页中添加该标本类型");
                }
            } else if (!orderItems.isEmpty()) {
                // 2) 回退: 查 lis_instrument_item_map.specimen_type (项目级), 空字符串视为未配置
                Set<Long> orderTestItemIds = orderItems.stream()
                        .map(OrderItem::getTestItemId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
                if (!orderTestItemIds.isEmpty()) {
                    String idPh = orderTestItemIds.stream().map(i -> "?").collect(Collectors.joining(","));
                    List<Object> argList = new ArrayList<>();
                    argList.add(dto.getInstrumentId());
                    argList.addAll(orderTestItemIds);
                    Map<Long, String> itemSpecimenMap = new java.util.HashMap<>();
                    try {
                        List<Map<String, Object>> mapRows = jdbcTemplate.queryForList(
                                "SELECT test_item_id, specimen_type FROM lis_instrument_item_map " +
                                "WHERE instrument_id = ? AND test_item_id IN (" + idPh + ")", argList.toArray());
                        for (Map<String, Object> row : mapRows) {
                            Long tiId = ((Number) row.get("test_item_id")).longValue();
                            Object spType = row.get("specimen_type");
                            String sp = spType == null ? null : spType.toString().trim();
                            itemSpecimenMap.put(tiId, (sp == null || sp.isEmpty()) ? null : sp);
                        }
                    } catch (Exception e) {
                        log.warn("查询仪器项目标本类型映射失败, instrumentId={}: {}", dto.getInstrumentId(), e.getMessage());
                    }
                    List<String> mismatchNames = orderItems.stream()
                            .filter(oi -> {
                                String sp = itemSpecimenMap.get(oi.getTestItemId());
                                return sp != null && !sp.equalsIgnoreCase(sampleType);
                            })
                            .map(OrderItem::getTestItemName)
                            .collect(Collectors.toList());
                    if (!mismatchNames.isEmpty()) {
                        throw new BusinessException("仪器不支持以标本类型[" + sampleType
                                + "]检测以下项目: " + String.join("、", mismatchNames)
                                + ", 请核对标本类型或仪器项目映射配置");
                    }
                }
            }
        }

        // 3. 按仪器生成序号标本号 (当前仪器最大标本号 + 1)
        String sampleNo = generateNextSampleNo(dto.getInstrumentId());

        // 4. 更新状态为已核收(2), 记录核收时间/仪器/标本号
        sample.setStatus(Sample.STATUS_RECEIVED);
        sample.setReceiveTime(LocalDateTime.now());
        sample.setInstrumentId(dto.getInstrumentId());
        sample.setSampleNo(sampleNo);
        if (dto.getOperatorId() != null) {
            sample.setOperatorId(dto.getOperatorId());
        }
        if (StringUtil.isNotEmpty(dto.getDepartment())) {
            sample.setDepartment(dto.getDepartment());
        }
        if (StringUtil.isNotEmpty(dto.getRemark())) {
            sample.setRemark(dto.getRemark());
        }
        sample.setUpdateTime(LocalDateTime.now());
        this.updateById(sample);

        // 发MQ消息 sample.received (MQ不可用时不阻断主流程)
        sendMqAsync("sample.received", sample);
        log.info("标本核收成功, barcode={}, instrumentId={}, sampleNo={}",
                sample.getSampleBarcode(), dto.getInstrumentId(), sampleNo);

        return toVO(sample);
    }

    /**
     * 核收前预检: 校验仪器是否能检测该条码对应的所有检验项目, 返回不可检测的项目名称列表
     * 返回空列表表示校验通过
     */
    @Override
    public List<String> precheckReceive(String barcode, Long instrumentId) {
        if (StringUtil.isEmpty(barcode)) {
            throw new BusinessException("标本条码不能为空");
        }
        if (instrumentId == null) {
            throw new BusinessException("仪器ID不能为空");
        }
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sample::getSampleBarcode, barcode);
        Sample sample = this.getOne(wrapper);
        if (sample == null) {
            throw new BusinessException("标本不存在: " + barcode);
        }
        if (instrumentItemMapMapper.countEnabledById(instrumentId) == 0) {
            throw new BusinessException("所选仪器不存在或已停用");
        }
        List<OrderItem> orderItems = listOrderItems(sample.getOrderId());
        if (orderItems.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> supportedItemIds = instrumentItemMapMapper
                .selectTestItemIdsByInstrumentId(instrumentId);
        Set<Long> supportedSet = new HashSet<>(supportedItemIds);
        return orderItems.stream()
                .filter(oi -> !supportedSet.contains(oi.getTestItemId()))
                .map(OrderItem::getTestItemName)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> lookupReceivable(String type, String keyword) {
        if (StringUtil.isEmpty(keyword)) {
            throw new BusinessException("请输入查询标识");
        }
        String kw = keyword.trim();
        // 门诊号/住院号按就诊类型过滤 (1门诊/2住院)
        Integer visitType = null;
        if ("outpatient".equalsIgnoreCase(type)) {
            visitType = 1;
        } else if ("inpatient".equalsIgnoreCase(type)) {
            visitType = 2;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT s.id, s.sample_barcode AS sampleBarcode, s.sample_no AS sampleNo, " +
                "s.patient_name AS patientName, s.sample_type AS sampleType, " +
                "s.container_type AS containerType, s.status, s.is_urgent AS isUrgent, " +
                "s.department, s.visit_no AS visitNo, s.collect_time AS collectTime, s.order_id AS orderId, " +
                "v.visit_type AS visitType, " +
                "(SELECT COALESCE(string_agg(oi.test_item_name, '、' ORDER BY oi.test_item_name), '') " +
                " FROM lis_order_item oi WHERE oi.order_id = s.order_id) AS testItems " +
                "FROM lis_sample s " +
                "LEFT JOIN lis_lab_order o ON s.order_id = o.id " +
                "LEFT JOIN lis_visit v ON v.id = COALESCE(s.visit_id, o.visit_id) " +
                "WHERE s.deleted = 0 AND s.status IN (2, 3) AND v.visit_no = ?");
        List<Object> args = new ArrayList<>();
        args.add(kw);
        if (visitType != null) {
            sql.append(" AND v.visit_type = ?");
            args.add(visitType);
        }
        sql.append(" ORDER BY s.is_urgent DESC, s.create_time DESC");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /**
     * 查询标本对应的所有申请项目 (按 order_id)
     */
    private List<OrderItem> listOrderItems(Long orderId) {
        if (orderId == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        return orderItemMapper.selectList(wrapper);
    }

    /**
     * 生成下一个标本号: 当前仪器已核收标本的最大序号 + 1 (从 1 开始)
     */
    private String generateNextSampleNo(Long instrumentId) {
        String maxNo = instrumentItemMapMapper.selectMaxSampleNoByInstrumentId(instrumentId);
        int next = 1;
        if (StringUtil.isNotEmpty(maxNo)) {
            try {
                next = Integer.parseInt(maxNo) + 1;
            } catch (NumberFormatException ignored) {
                next = 1;
            }
        }
        return String.valueOf(next);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleVO collectSample(SampleCollectDTO dto, Long currentUserId) {
        Sample sample = null;
        // 优先按条码查询
        if (StringUtil.isNotEmpty(dto.getSampleBarcode())) {
            LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Sample::getSampleBarcode, dto.getSampleBarcode());
            sample = this.getOne(wrapper);
            if (sample == null) {
                throw new BusinessException("标本不存在, 条码: " + dto.getSampleBarcode());
            }
        } else if (dto.getSampleId() != null) {
            sample = super.getById(dto.getSampleId());
            if (sample == null) {
                throw new BusinessException("标本不存在, ID: " + dto.getSampleId());
            }
        } else {
            throw new BusinessException("标本条码或ID不能同时为空");
        }
        // 校验状态: 只有"已申请"状态可以采集
        if (sample.getStatus() != null && sample.getStatus() >= Sample.STATUS_COLLECTED) {
            throw new BusinessException("标本已采集, 不可重复采集. 当前状态: "
                    + Sample.statusText(sample.getStatus()));
        }
        // 采集时间: 优先使用传入时间, 否则使用当前系统时间
        LocalDateTime collectTime = LocalDateTime.now();
        if (StringUtil.isNotEmpty(dto.getCollectTime())) {
            try {
                collectTime = LocalDateTime.parse(dto.getCollectTime());
            } catch (Exception e) {
                // 解析失败, 使用当前时间
                log.warn("采集时间解析失败, 使用当前系统时间: {}", dto.getCollectTime());
            }
        }
        // 采集人: 通过当前登录用户ID查询lis_staff获取员工ID
        StaffInfo staff = resolveStaffByUserId(currentUserId);
        Long collectorId = staff != null ? staff.id : currentUserId;
        sample.setStatus(Sample.STATUS_COLLECTED);
        sample.setCollectTime(collectTime);
        sample.setCollectorId(collectorId);
        // 采集备注
        if (StringUtil.isNotEmpty(dto.getRemark())) {
            sample.setRemark(dto.getRemark());
        }
        sample.setUpdateTime(LocalDateTime.now());
        this.updateById(sample);

        // 发MQ消息 sample.collected (MQ不可用时不阻断主流程)
        sendMqAsync("sample.collected", sample);
        log.info("标本采集成功, barcode={}, collectTime={}, collectorId={}, userId={}",
                sample.getSampleBarcode(), collectTime, collectorId, currentUserId);
        return toVO(sample);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SampleVO> deliverSamples(List<String> barcodes, Long currentUserId) {
        if (barcodes == null || barcodes.isEmpty()) {
            throw new BusinessException("条码列表不能为空");
        }
        // 通过当前登录用户ID查询lis_staff获取员工ID+姓名
        StaffInfo staff = resolveStaffByUserId(currentUserId);
        Long deliverPersonId = staff != null ? staff.id : null;
        String personName = staff != null ? staff.name : ("用户" + (currentUserId == null ? "" : currentUserId));
        List<SampleVO> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String barcode : barcodes) {
            LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Sample::getSampleBarcode, barcode);
            Sample sample = this.getOne(wrapper);
            if (sample == null) {
                throw new BusinessException("标本不存在, 条码: " + barcode);
            }
            // 校验: 必须已采集才能送检
            if (sample.getStatus() == null || sample.getStatus() < Sample.STATUS_COLLECTED) {
                throw new BusinessException("标本未采集, 不可送检. 条码: " + barcode
                        + ", 当前状态: " + (sample.getStatus() != null ? Sample.statusText(sample.getStatus()) : "未知"));
            }
            if (sample.getStatus() >= Sample.STATUS_DELIVERED) {
                throw new BusinessException("标本已" + Sample.statusText(sample.getStatus()) + ", 不可重复送检. 条码: " + barcode);
            }
            sample.setStatus(Sample.STATUS_DELIVERED);
            sample.setDeliverTime(now);
            sample.setDeliverPerson(personName);
            sample.setDeliverPersonId(deliverPersonId);
            sample.setUpdateTime(now);
            this.updateById(sample);
            log.info("标本送检成功, barcode={}, deliverPerson={}, deliverPersonId={}, userId={}",
                    barcode, personName, deliverPersonId, currentUserId);
            results.add(toVO(sample));
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SampleVO> signOffSamples(List<String> barcodes, Long currentUserId) {
        if (barcodes == null || barcodes.isEmpty()) {
            throw new BusinessException("条码列表不能为空");
        }
        // 通过当前登录用户ID查询lis_staff获取员工ID+姓名
        StaffInfo staff = resolveStaffByUserId(currentUserId);
        Long signOffPersonId = staff != null ? staff.id : null;
        String personName = staff != null ? staff.name : ("用户" + (currentUserId == null ? "" : currentUserId));
        List<SampleVO> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String barcode : barcodes) {
            LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Sample::getSampleBarcode, barcode);
            Sample sample = this.getOne(wrapper);
            if (sample == null) {
                throw new BusinessException("标本不存在, 条码: " + barcode);
            }
            // 校验: 必须已送检
            if (sample.getStatus() == null || sample.getStatus() < Sample.STATUS_DELIVERED) {
                throw new BusinessException("标本未送检, 不可签收. 条码: " + barcode
                        + ", 当前状态: " + (sample.getStatus() != null ? Sample.statusText(sample.getStatus()) : "未知"));
            }
            // 校验: 不可重复签收
            if (sample.getStatus() >= Sample.STATUS_SIGNED) {
                throw new BusinessException("标本已" + Sample.statusText(sample.getStatus()) + ", 不可重复签收. 条码: " + barcode);
            }
            sample.setStatus(Sample.STATUS_SIGNED);
            sample.setSignOffTime(now);
            sample.setSignOffPerson(personName);
            sample.setSignOffPersonId(signOffPersonId);
            sample.setUpdateTime(now);
            this.updateById(sample);
            log.info("标本签收成功, barcode={}, signOffPerson={}, signOffPersonId={}, userId={}",
                    barcode, personName, signOffPersonId, currentUserId);
            results.add(toVO(sample));
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SampleVO> cancelDeliverSamples(List<String> barcodes, Long currentUserId) {
        if (barcodes == null || barcodes.isEmpty()) {
            throw new BusinessException("条码列表不能为空");
        }
        List<SampleVO> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String barcode : barcodes) {
            LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Sample::getSampleBarcode, barcode);
            Sample sample = this.getOne(wrapper);
            if (sample == null) {
                throw new BusinessException("标本不存在, 条码: " + barcode);
            }
            // 校验: 必须已送检 (status == STATUS_DELIVERED)
            if (sample.getStatus() == null || sample.getStatus() != Sample.STATUS_DELIVERED) {
                throw new BusinessException("标本未送检或已" + (sample.getStatus() != null ? Sample.statusText(sample.getStatus()) : "未知")
                        + ", 无需取消. 条码: " + barcode);
            }
            // 校验: 已签收/核收/报告/退费/作废/拒收的标本不可取消送检 (status > STATUS_DELIVERED)
            if (sample.getStatus() > Sample.STATUS_DELIVERED) {
                throw new BusinessException("标本已" + Sample.statusText(sample.getStatus()) + ", 不可取消送检. 条码: " + barcode);
            }
            // 回退到已采集状态, 清除送检信息 (LambdaUpdateWrapper显式set null, 绕过MyBatis-Plus默认NOT_NULL策略)
            sample.setStatus(Sample.STATUS_COLLECTED);
            sample.setDeliverTime(null);
            sample.setDeliverPerson(null);
            sample.setDeliverPersonId(null);
            sample.setUpdateTime(now);
            LambdaUpdateWrapper<Sample> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Sample::getId, sample.getId())
                    .set(Sample::getStatus, Sample.STATUS_COLLECTED)
                    .set(Sample::getDeliverTime, null)
                    .set(Sample::getDeliverPerson, null)
                    .set(Sample::getDeliverPersonId, null)
                    .set(Sample::getUpdateTime, now);
            this.update(updateWrapper);
            log.info("标本取消送检成功, barcode={}, userId={}", barcode, currentUserId);
            results.add(toVO(sample));
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SampleVO> cancelSignOffSamples(List<String> barcodes, Long currentUserId) {
        if (barcodes == null || barcodes.isEmpty()) {
            throw new BusinessException("条码列表不能为空");
        }
        List<SampleVO> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String barcode : barcodes) {
            LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Sample::getSampleBarcode, barcode);
            Sample sample = this.getOne(wrapper);
            if (sample == null) {
                throw new BusinessException("标本不存在, 条码: " + barcode);
            }
            // 校验: 必须已签收 (status == STATUS_SIGNED)
            if (sample.getStatus() == null || sample.getStatus() != Sample.STATUS_SIGNED) {
                throw new BusinessException("标本未签收或已" + (sample.getStatus() != null ? Sample.statusText(sample.getStatus()) : "未知")
                        + ", 无需取消签收. 条码: " + barcode);
            }
            // 校验: 已核收/检测中/已报告/退费/作废/拒收的标本不可取消签收 (status > STATUS_SIGNED)
            if (sample.getStatus() > Sample.STATUS_SIGNED) {
                throw new BusinessException("标本已" + Sample.statusText(sample.getStatus()) + ", 不可取消签收. 条码: " + barcode);
            }
            // 回退到已送检状态, 清除签收信息 (LambdaUpdateWrapper显式set null, 绕过MyBatis-Plus默认NOT_NULL策略)
            sample.setStatus(Sample.STATUS_DELIVERED);
            sample.setSignOffTime(null);
            sample.setSignOffPerson(null);
            sample.setSignOffPersonId(null);
            sample.setUpdateTime(now);
            LambdaUpdateWrapper<Sample> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Sample::getId, sample.getId())
                    .set(Sample::getStatus, Sample.STATUS_DELIVERED)
                    .set(Sample::getSignOffTime, null)
                    .set(Sample::getSignOffPerson, null)
                    .set(Sample::getSignOffPersonId, null)
                    .set(Sample::getUpdateTime, now);
            this.update(updateWrapper);
            log.info("标本取消签收成功, barcode={}, userId={}", barcode, currentUserId);
            results.add(toVO(sample));
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SampleVO> cancelCollectSamples(List<String> barcodes, Long currentUserId) {
        if (barcodes == null || barcodes.isEmpty()) {
            throw new BusinessException("条码列表不能为空");
        }
        List<SampleVO> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String barcode : barcodes) {
            LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Sample::getSampleBarcode, barcode);
            Sample sample = this.getOne(wrapper);
            if (sample == null) {
                throw new BusinessException("标本不存在, 条码: " + barcode);
            }
            // 校验: 必须已采集 (status == STATUS_COLLECTED), 未送检才可取消采集
            if (sample.getStatus() == null || sample.getStatus() != Sample.STATUS_COLLECTED) {
                String currentStatus = (sample.getStatus() != null) ? Sample.statusText(sample.getStatus()) : "未知";
                throw new BusinessException("标本" + (sample.getStatus() == null ? "未采集" : ("当前状态为" + currentStatus))
                        + ", 不可取消采集. 条码: " + barcode);
            }
            // 回退到已申请状态, 清除采集信息
            sample.setStatus(Sample.STATUS_APPLIED);
            sample.setCollectTime(null);
            sample.setCollectorId(null);
            sample.setUpdateTime(now);
            LambdaUpdateWrapper<Sample> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Sample::getId, sample.getId())
                    .set(Sample::getStatus, Sample.STATUS_APPLIED)
                    .set(Sample::getCollectTime, null)
                    .set(Sample::getCollectorId, null)
                    .set(Sample::getUpdateTime, now);
            this.update(updateWrapper);
            log.info("标本取消采集成功, barcode={}, userId={}", barcode, currentUserId);
            results.add(toVO(sample));
        }
        return results;
    }

    /** 员工信息 (内部使用) */
    private static class StaffInfo {
        Long id;
        String name;
    }

    /**
     * 通过当前登录用户ID (sys_user.id) 查询 lis_staff 获取员工ID+姓名
     */
    private StaffInfo resolveStaffByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, name FROM lis_staff WHERE user_id = ?",
                    (rs, rowNum) -> {
                        StaffInfo info = new StaffInfo();
                        info.id = rs.getLong("id");
                        info.name = rs.getString("name");
                        return info;
                    }, userId);
        } catch (Exception e) {
            log.warn("通过user_id={}查询lis_staff失败: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 判断申请单是否为急诊 (visit_type==3)
     * 优先取 lis_visit.visit_type, 回退取 lis_lab_order.order_type (1门诊 2住院 3急诊)
     */
    private boolean isEmergencyOrder(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return findEmergencyOrderIds(java.util.Collections.singleton(orderId)).contains(orderId);
    }

    /**
     * 批量查询急诊申请单ID集合 (visit_type==3 或 order_type==3 视为急诊)
     * 通过 lis_lab_order LEFT JOIN lis_visit 获取就诊类型
     */
    private Set<Long> findEmergencyOrderIds(java.util.Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> unique = orderIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return new HashSet<>();
        }
        String idPh = unique.stream().map(i -> "?").collect(Collectors.joining(","));
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT lo.id AS order_id, v.visit_type AS visit_type, lo.order_type AS order_type " +
                    "FROM lis_lab_order lo LEFT JOIN lis_visit v ON lo.visit_id = v.id " +
                    "WHERE lo.id IN (" + idPh + ")", unique.toArray());
            Set<Long> result = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Long oid = ((Number) row.get("order_id")).longValue();
                Object visitType = row.get("visit_type");
                Object orderType = row.get("order_type");
                boolean emergency = (visitType != null && ((Number) visitType).intValue() == 3)
                        || (orderType != null && ((Number) orderType).intValue() == 3);
                if (emergency) {
                    result.add(oid);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("批量查询申请急诊状态失败: {}", e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * 通过 lis_staff.id 查询员工姓名; 若查不到则返回 fallbackName
     */
    private String resolveStaffName(Long staffId, String fallbackName) {
        if (staffId != null) {
            try {
                String name = jdbcTemplate.queryForObject(
                        "SELECT name FROM lis_staff WHERE id = ?",
                        String.class, staffId);
                if (StringUtil.isNotEmpty(name)) {
                    return name;
                }
            } catch (Exception e) {
                log.warn("通过lis_staff.id={}查询姓名失败: {}", staffId, e.getMessage());
            }
        }
        return StringUtil.isNotEmpty(fallbackName) ? fallbackName : ("员工" + (staffId == null ? "" : staffId));
    }

    @Override
    public SampleVO getByBarcode(String barcode) {
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sample::getSampleBarcode, barcode);
        Sample sample = this.getOne(wrapper);
        return sample == null ? null : toVO(sample);
    }

    @Override
    public SampleVO getById(Long id) {
        Sample sample = super.getById(id);
        return sample == null ? null : toVO(sample);
    }

    @Override
    public Page<SampleVO> queryPage(SampleQueryDTO query) {
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
        if (StringUtil.isNotEmpty(query.getSampleBarcode())) {
            wrapper.eq(Sample::getSampleBarcode, query.getSampleBarcode());
        }
        if (StringUtil.isNotEmpty(query.getVisitNo())) {
            wrapper.eq(Sample::getVisitNo, query.getVisitNo());
        }
        if (StringUtil.isNotEmpty(query.getPatientName())) {
            wrapper.like(Sample::getPatientName, query.getPatientName());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Sample::getStatus, query.getStatus());
        }
        if (query.getIsUrgent() != null) {
            wrapper.eq(Sample::getIsUrgent, query.getIsUrgent());
        }
        if (StringUtil.isNotEmpty(query.getDepartment())) {
            wrapper.eq(Sample::getDepartment, query.getDepartment());
        }
        if (StringUtil.isNotEmpty(query.getStartDate())) {
            wrapper.ge(Sample::getCollectTime, parseStartOfDay(query.getStartDate()));
        }
        if (StringUtil.isNotEmpty(query.getEndDate())) {
            wrapper.le(Sample::getCollectTime, parseStartOfDay(query.getEndDate()).plusDays(1));
        }
        if (StringUtil.isNotEmpty(query.getSignOffStartDate())) {
            wrapper.ge(Sample::getSignOffTime, parseStartOfDay(query.getSignOffStartDate()));
        }
        if (StringUtil.isNotEmpty(query.getSignOffEndDate())) {
            wrapper.le(Sample::getSignOffTime, parseStartOfDay(query.getSignOffEndDate()).plusDays(1));
        }
        if (StringUtil.isNotEmpty(query.getDeliverStartDate())) {
            wrapper.ge(Sample::getDeliverTime, parseStartOfDay(query.getDeliverStartDate()));
        }
        if (StringUtil.isNotEmpty(query.getDeliverEndDate())) {
            wrapper.le(Sample::getDeliverTime, parseStartOfDay(query.getDeliverEndDate()).plusDays(1));
        }
        if (query.getOrderId() != null) {
            wrapper.eq(Sample::getOrderId, query.getOrderId());
        }
        if (query.getVisitId() != null) {
            wrapper.eq(Sample::getVisitId, query.getVisitId());
        }
        if (query.getVisitIds() != null && !query.getVisitIds().isEmpty()) {
            wrapper.in(Sample::getVisitId, query.getVisitIds());
        }
        if (query.getCollectorId() != null) {
            wrapper.eq(Sample::getCollectorId, query.getCollectorId());
        }
        if (StringUtil.isNotEmpty(query.getDeliverPerson())) {
            wrapper.eq(Sample::getDeliverPerson, query.getDeliverPerson());
        }
        if (StringUtil.isNotEmpty(query.getSignOffPerson())) {
            wrapper.eq(Sample::getSignOffPerson, query.getSignOffPerson());
        }
        wrapper.orderByDesc(Sample::getIsUrgent)   // 急诊优先
               .orderByDesc(Sample::getCreateTime);

        Page<Sample> page = this.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        Page<SampleVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<Sample> records = page.getRecords();
        if (records.isEmpty()) {
            result.setRecords(new ArrayList<>());
            return result;
        }
        // 批量查询采集人姓名 (消除N+1)
        Set<Long> collectorIds = records.stream()
                .map(Sample::getCollectorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> collectorNameMap = new java.util.HashMap<>();
        if (!collectorIds.isEmpty()) {
            String idPh = collectorIds.stream().map(i -> "?").collect(Collectors.joining(","));
            List<Map<String, Object>> nameRows = jdbcTemplate.queryForList(
                    "SELECT id, name FROM lis_staff WHERE id IN (" + idPh + ")",
                    collectorIds.toArray());
            for (Map<String, Object> row : nameRows) {
                collectorNameMap.put(((Number) row.get("id")).longValue(), row.get("name").toString());
            }
        }
        // 批量查询仪器名称
        Set<Long> instrumentIds = records.stream()
                .map(Sample::getInstrumentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> instrumentNameMap = new java.util.HashMap<>();
        if (!instrumentIds.isEmpty()) {
            for (Long instrId : instrumentIds) {
                try {
                    String name = instrumentItemMapMapper.selectInstrumentNameById(instrId);
                    if (StringUtil.isNotEmpty(name)) instrumentNameMap.put(instrId, name);
                } catch (Exception ignored) {}
            }
        }
        // 批量查询检验项目 (orderItems) - 通过 lis_sample_order_item 中间表, 支持合管标本关联多个项目
        Set<Long> sampleIds = records.stream()
                .map(Sample::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<SampleVO.OrderItemVO>> sampleOrderItemsMap = new java.util.HashMap<>();
        if (!sampleIds.isEmpty()) {
            // 1. 通过中间表查标本关联的所有orderItemId
            LambdaQueryWrapper<SampleOrderItem> soiWrapper = new LambdaQueryWrapper<>();
            soiWrapper.in(SampleOrderItem::getSampleId, sampleIds);
            soiWrapper.orderByAsc(SampleOrderItem::getId);
            List<SampleOrderItem> soiList = sampleOrderItemMapper.selectList(soiWrapper);

            // 兼容: 如果中间表没有记录(旧数据或非合管场景), 退化为按orderId查
            if (soiList.isEmpty()) {
                Set<Long> orderIds = records.stream()
                        .map(Sample::getOrderId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
                if (!orderIds.isEmpty()) {
                    LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
                    oiWrapper.in(OrderItem::getOrderId, orderIds);
                    List<OrderItem> allOrderItems = orderItemMapper.selectList(oiWrapper);
                    // 先建立orderId -> samples映射
                    Map<Long, List<Sample>> samplesByOrderId = records.stream()
                            .filter(s -> s.getOrderId() != null)
                            .collect(Collectors.groupingBy(Sample::getOrderId));
                    for (OrderItem oi : allOrderItems) {
                        SampleVO.OrderItemVO vo = new SampleVO.OrderItemVO();
                        BeanUtils.copyProperties(oi, vo);
                        List<Sample> sampleList = samplesByOrderId.get(oi.getOrderId());
                        if (sampleList != null) {
                            for (Sample s : sampleList) {
                                sampleOrderItemsMap.computeIfAbsent(s.getId(), k -> new ArrayList<>()).add(vo);
                            }
                        }
                    }
                }
            } else {
                // 2. 批量查OrderItem详情
                Set<Long> orderItemIds = soiList.stream()
                        .map(SampleOrderItem::getOrderItemId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
                Map<Long, OrderItem> oiMap = new java.util.HashMap<>();
                if (!orderItemIds.isEmpty()) {
                    LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
                    oiWrapper.in(OrderItem::getId, orderItemIds);
                    oiMap = orderItemMapper.selectList(oiWrapper).stream()
                            .collect(Collectors.toMap(OrderItem::getId, oi -> oi, (a, b) -> a));
                }
                // 3. 按sampleId分组
                for (SampleOrderItem soi : soiList) {
                    OrderItem oi = oiMap.get(soi.getOrderItemId());
                    if (oi == null) continue;
                    SampleVO.OrderItemVO vo = new SampleVO.OrderItemVO();
                    BeanUtils.copyProperties(oi, vo);
                    sampleOrderItemsMap.computeIfAbsent(soi.getSampleId(), k -> new ArrayList<>()).add(vo);
                }
            }
        }
        // 构建VO (批量加载检验项目)
        Map<Long, List<SampleVO.OrderItemVO>> finalOrderItemsMap = sampleOrderItemsMap;
        List<SampleVO> voList = records.stream().map(sample -> {
            SampleVO vo = new SampleVO();
            BeanUtils.copyProperties(sample, vo);
            if (sample.getStatus() != null) {
                vo.setStatusText(Sample.statusText(sample.getStatus()));
            }
            if (sample.getInstrumentId() != null) {
                vo.setInstrumentName(instrumentNameMap.get(sample.getInstrumentId()));
            }
            if (sample.getCollectorId() != null) {
                vo.setCollectorName(collectorNameMap.getOrDefault(sample.getCollectorId(),
                        "用户" + sample.getCollectorId()));
            }
            // 填充检验项目
            List<SampleVO.OrderItemVO> orderItems = finalOrderItemsMap.getOrDefault(sample.getId(), new ArrayList<>());
            vo.setOrderItems(orderItems);
            return vo;
        }).collect(Collectors.toList());
        result.setRecords(voList);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleVO updateStatus(Long id, int status) {
        Sample sample = super.getById(id);
        if (sample == null) {
            throw new BusinessException("标本不存在: " + id);
        }
        sample.setStatus(status);
        // 根据状态补充时间戳
        switch (status) {
            case Sample.STATUS_TESTING -> sample.setTestStartTime(LocalDateTime.now());
            case Sample.STATUS_REPORTED -> sample.setReportTime(LocalDateTime.now());
            default -> { }
        }
        sample.setUpdateTime(LocalDateTime.now());
        this.updateById(sample);
        return toVO(sample);
    }

    @Override
    public SampleVO trackSample(Long id) {
        Sample sample = super.getById(id);
        if (sample == null) {
            throw new BusinessException("标本不存在: " + id);
        }
        SampleVO vo = toVO(sample);
        // 构建全生命周期时间线
        List<SampleVO.TrackNode> nodes = new ArrayList<>();
        nodes.add(SampleVO.TrackNode.builder()
                .status(Sample.STATUS_APPLIED)
                .statusText(Sample.statusText(Sample.STATUS_APPLIED))
                .time(sample.getCreateTime())
                .operator(String.valueOf(sample.getOperatorId()))
                .build());
        if (sample.getCollectTime() != null) {
            nodes.add(SampleVO.TrackNode.builder()
                    .status(Sample.STATUS_COLLECTED)
                    .statusText(Sample.statusText(Sample.STATUS_COLLECTED))
                    .time(sample.getCollectTime())
                    .operator(String.valueOf(sample.getOperatorId()))
                    .build());
        }
        if (sample.getDeliverTime() != null) {
            nodes.add(SampleVO.TrackNode.builder()
                    .status(Sample.STATUS_DELIVERED)
                    .statusText(Sample.statusText(Sample.STATUS_DELIVERED))
                    .time(sample.getDeliverTime())
                    .operator(sample.getDeliverPerson())
                    .build());
        }
        if (sample.getSignOffTime() != null) {
            nodes.add(SampleVO.TrackNode.builder()
                    .status(Sample.STATUS_SIGNED)
                    .statusText(Sample.statusText(Sample.STATUS_SIGNED))
                    .time(sample.getSignOffTime())
                    .operator(sample.getSignOffPerson())
                    .build());
        }
        if (sample.getReceiveTime() != null) {
            nodes.add(SampleVO.TrackNode.builder()
                    .status(Sample.STATUS_RECEIVED)
                    .statusText(Sample.statusText(Sample.STATUS_RECEIVED))
                    .time(sample.getReceiveTime())
                    .operator(String.valueOf(sample.getOperatorId()))
                    .build());
        }
        if (sample.getTestStartTime() != null) {
            nodes.add(SampleVO.TrackNode.builder()
                    .status(Sample.STATUS_TESTING)
                    .statusText(Sample.statusText(Sample.STATUS_TESTING))
                    .time(sample.getTestStartTime())
                    .operator(String.valueOf(sample.getOperatorId()))
                    .build());
        }
        if (sample.getReportTime() != null) {
            nodes.add(SampleVO.TrackNode.builder()
                    .status(Sample.STATUS_REPORTED)
                    .statusText(Sample.statusText(Sample.STATUS_REPORTED))
                    .time(sample.getReportTime())
                    .operator(String.valueOf(sample.getOperatorId()))
                    .build());
        }
        vo.setTrackNodes(nodes);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleVO refundSample(Long id, String reason) {
        Sample sample = super.getById(id);
        if (sample == null) {
            throw new BusinessException("标本不存在: " + id);
        }
        if (sample.getStatus() != null
                && (sample.getStatus() == Sample.STATUS_REFUNDED
                || sample.getStatus() == Sample.STATUS_VOIDED)) {
            throw new BusinessException("标本已退费或已作废, 不可重复操作");
        }
        if (sample.getStatus() != null && sample.getStatus() == Sample.STATUS_REPORTED) {
            throw new BusinessException("标本已报告, 不可退费");
        }
        sample.setStatus(Sample.STATUS_REFUNDED);
        sample.setRemark(StringUtil.isNotEmpty(reason) ? reason : "标本退费");
        sample.setUpdateTime(LocalDateTime.now());
        this.updateById(sample);

        // 发 MQ 消息 sample.refunded (MQ不可用时不阻断主流程)
        sendMqAsync("sample.refunded", sample);
        log.info("标本退费成功, 发送 sample.refunded 消息, id={}, reason={}", id, reason);
        return toVO(sample);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleVO voidSample(Long id, String reason) {
        Sample sample = super.getById(id);
        if (sample == null) {
            throw new BusinessException("标本不存在: " + id);
        }
        if (sample.getStatus() != null && sample.getStatus() == Sample.STATUS_VOIDED) {
            throw new BusinessException("标本已作废, 不可重复操作");
        }
        if (sample.getStatus() != null && sample.getStatus() == Sample.STATUS_REPORTED) {
            throw new BusinessException("标本已报告, 不可作废");
        }
        sample.setStatus(Sample.STATUS_VOIDED);
        sample.setRemark(StringUtil.isNotEmpty(reason) ? reason : "标本作废");
        sample.setUpdateTime(LocalDateTime.now());
        this.updateById(sample);
        log.info("标本作废成功, id={}, reason={}", id, reason);
        return toVO(sample);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleVO rejectSample(SampleRejectDTO dto, Long currentUserId) {
        if (StringUtil.isEmpty(dto.getBarcode())) {
            throw new BusinessException("标本条码不能为空");
        }
        if (StringUtil.isEmpty(dto.getRejectType())) {
            throw new BusinessException("请选择不合格类型");
        }
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sample::getSampleBarcode, dto.getBarcode());
        Sample sample = this.getOne(wrapper);
        if (sample == null) {
            throw new BusinessException("标本不存在, 条码: " + dto.getBarcode());
        }
        // 已报告/已核收的标本不可拒收
        if (sample.getStatus() != null
                && (sample.getStatus() == Sample.STATUS_REPORTED
                || sample.getStatus() == Sample.STATUS_REFUNDED
                || sample.getStatus() == Sample.STATUS_VOIDED)) {
            throw new BusinessException("标本当前状态(" + Sample.statusText(sample.getStatus())
                    + ")不可拒收. 条码: " + dto.getBarcode());
        }
        // 拒收次数累加
        int cnt = sample.getRejectCount() == null ? 0 : sample.getRejectCount();
        sample.setRejectCount(cnt + 1);
        sample.setStatus(Sample.STATUS_REJECTED);
        sample.setRejectTime(LocalDateTime.now());
        // 通过当前登录用户ID查询lis_staff获取员工ID+姓名
        StaffInfo staff = resolveStaffByUserId(currentUserId);
        Long rejectPersonId = staff != null ? staff.id : null;
        String personName = staff != null ? staff.name : ("用户" + (currentUserId == null ? "" : currentUserId));
        sample.setRejectPerson(personName);
        sample.setRejectPersonId(rejectPersonId);
        sample.setRejectType(dto.getRejectType());
        sample.setRejectReason(StringUtil.isNotEmpty(dto.getRejectReason()) ? dto.getRejectReason() : dto.getRejectType());
        sample.setUpdateTime(LocalDateTime.now());
        this.updateById(sample);

        // 发MQ消息 sample.rejected (MQ不可用时不阻断主流程)
        sendMqAsync("sample.rejected", sample);
        log.info("标本拒收成功, barcode={}, rejectType={}, rejectPerson={}, rejectPersonId={}, userId={}, rejectCount={}",
                sample.getSampleBarcode(), dto.getRejectType(), personName, rejectPersonId, currentUserId, sample.getRejectCount());
        return toVO(sample);
    }

    /**
     * 实体转 VO (附带仪器名称 + 检验项目列表 + 采集人姓名)
     */
    private SampleVO toVO(Sample sample) {
        SampleVO vo = new SampleVO();
        BeanUtils.copyProperties(sample, vo);
        if (sample.getStatus() != null) {
            vo.setStatusText(Sample.statusText(sample.getStatus()));
        }
        // 仪器名称 (单次查询, 仅当有 instrumentId 时)
        if (sample.getInstrumentId() != null) {
            try {
                vo.setInstrumentName(instrumentItemMapMapper.selectInstrumentNameById(sample.getInstrumentId()));
            } catch (Exception ignored) {}
        }
        // 采集人姓名 (单次查询, 仅当有 collectorId 时)
        if (sample.getCollectorId() != null) {
            try {
                String name = jdbcTemplate.queryForObject(
                        "SELECT name FROM lis_staff WHERE id = ?",
                        String.class, sample.getCollectorId());
                vo.setCollectorName(StringUtil.isNotEmpty(name) ? name : ("用户" + sample.getCollectorId()));
            } catch (Exception e) {
                vo.setCollectorName("用户" + sample.getCollectorId());
            }
        }
        // 加载检验项目列表
        if (sample.getOrderId() != null) {
            List<OrderItem> orderItems = listOrderItems(sample.getOrderId());
            List<SampleVO.OrderItemVO> itemVOs = orderItems.stream()
                    .map(oi -> {
                        SampleVO.OrderItemVO itemVO = new SampleVO.OrderItemVO();
                        BeanUtils.copyProperties(oi, itemVO);
                        return itemVO;
                    })
                    .collect(Collectors.toList());
            vo.setOrderItems(itemVOs);
        }
        return vo;
    }

    private LocalDateTime parseStartOfDay(String dateStr) {
        return LocalDateTime.parse(dateStr + "T00:00:00");
    }

    // ==================== 试管分组计算 ====================

    @Override
    public List<TubeGroupVO> calculateTubeGroups(Long visitId) {
        // 跨申请合管: 按visitId查询该就诊下所有申请的检验项目
        // 单次 JOIN 查询: 申请项目 + 检验项目信息 + 试管信息 + 申请信息(过滤visitId)
        // 已生成条码的申请项目 (在 lis_sample_order_item 中有关联记录) 不再参与合管
        // 新增: 通过 lis_culture_plan 判断该检验项目是否为微生物项目 (绑定了培养方案=微生物项目)
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT oi.id AS order_item_id, oi.order_id, oi.test_item_id, oi.test_item_name AS oi_item_name, " +
                "       ti.item_code, ti.item_name AS ti_item_name, ti.specimen_type, ti.tube_id, ti.category, " +
                "       tt.tube_name, tt.tube_color, " +
                "       CASE WHEN cp.id IS NOT NULL THEN 1 ELSE 0 END AS is_micro " +
                "FROM lis_order_item oi " +
                "INNER JOIN lis_lab_order lo ON oi.order_id = lo.id " +
                "LEFT JOIN lis_test_item ti ON oi.test_item_id = ti.id " +
                "LEFT JOIN lis_test_tube tt ON ti.tube_id = tt.id " +
                "LEFT JOIN lis_culture_plan cp ON cp.test_item_id = ti.id AND cp.status = 1 " +
                "WHERE lo.visit_id = ? AND lo.status != 2 " +  // 排除已取消的申请
                "  AND oi.id NOT IN (SELECT order_item_id FROM lis_sample_order_item)",  // 排除已生成条码的项目
                visitId);
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }

        // 按试管ID + 标本类型分组合管 (跨申请合并)
        // 合管规则:
        //   1. 微生物项目 (is_micro=1 或 category=微生物 或 名称含"培养"):
        //      每个项目独立一管, 不与其他项目(哪怕同试管同标本类型)合管
        //   2. 普通项目: 相同试管 + 相同标本类型 = 一根管 (可跨申请合并)
        //   3. 未绑定试管的检验项目各自独立一管
        Map<String, TubeGroupVO> groupMap = new LinkedHashMap<>();
        int groupIndex = 1;
        for (Map<String, Object> row : rows) {
            Long orderItemId = row.get("order_item_id") != null ? ((Number) row.get("order_item_id")).longValue() : null;
            Long orderId = row.get("order_id") != null ? ((Number) row.get("order_id")).longValue() : null;
            Long testItemId = row.get("test_item_id") != null ? ((Number) row.get("test_item_id")).longValue() : null;
            Long tubeId = row.get("tube_id") != null ? ((Number) row.get("tube_id")).longValue() : null;
            String specimenType = row.get("specimen_type") != null ? row.get("specimen_type").toString() : "未知";
            String itemCode = row.get("item_code") != null ? row.get("item_code").toString() : "";
            String itemName = row.get("ti_item_name") != null ? row.get("ti_item_name").toString()
                    : (row.get("oi_item_name") != null ? row.get("oi_item_name").toString() : "");
            String tubeName = row.get("tube_name") != null ? row.get("tube_name").toString() : "未绑定试管";
            String tubeColor = row.get("tube_color") != null ? row.get("tube_color").toString() : "";
            Object categoryObj = row.get("category");
            String category = categoryObj != null ? categoryObj.toString() : "";

            // 判断微生物项目: 1) 培养方案绑定 is_micro=1 2) category含微生物 3) 名称含"培养"
            boolean isMicro = false;
            Object isMicroObj = row.get("is_micro");
            if (isMicroObj != null && ((Number) isMicroObj).intValue() == 1) {
                isMicro = true;
            } else if (category.contains("微生物") || category.contains("细菌") || category.contains("micro")) {
                isMicro = true;
            } else if (itemName.contains("培养") || itemName.contains("药敏") || itemName.contains("涂片")) {
                isMicro = true;
            }

            // 分组key:
            // - 微生物项目: 用 testItemId 单独分组 -> 保证每个微生物项目独立一管, 不合管
            // - 未绑定试管的每个项目: 独立分组 (none_{testItemId})
            // - 普通项目: 按 tubeId + specimenType 合管
            String groupKey;
            if (isMicro) {
                groupKey = "micro_" + testItemId;
            } else if (tubeId != null) {
                groupKey = "tube_" + tubeId + "_" + specimenType;
            } else {
                groupKey = "none_" + testItemId;
            }

            TubeGroupVO group = groupMap.get(groupKey);
            if (group == null) {
                group = TubeGroupVO.builder()
                        .groupIndex(groupIndex++)
                        .tubeId(tubeId)
                        .tubeName(tubeName)
                        .tubeColor(tubeColor)
                        .sampleType(specimenType)
                        .containerType(tubeName)
                        .testItems(new ArrayList<>())
                        .build();
                groupMap.put(groupKey, group);
            }
            group.getTestItems().add(TubeGroupVO.TestItemInfo.builder()
                    .testItemId(testItemId)
                    .itemCode(itemCode)
                    .itemName(itemName)
                    .specimenType(specimenType)
                    .tubeId(tubeId)
                    .orderId(orderId)
                    .orderItemId(orderItemId)
                    .build());
        }

        // 设置项目数并返回
        List<TubeGroupVO> groups = new ArrayList<>(groupMap.values());
        for (TubeGroupVO g : groups) {
            g.setItemCount(g.getTestItems().size());
        }
        return groups;
    }

    // ==================== 批量生成条码 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SampleVO> batchCreateBarcodes(BatchBarcodeDTO dto) {
        if (dto.getGroups() == null || dto.getGroups().isEmpty()) {
            throw new BusinessException("试管分组列表不能为空");
        }
        // 校验: 已生成条码的申请项目不可重复生成
        // 收集本次请求涉及的所有 orderItemId
        List<Long> requestOrderItemIds = new ArrayList<>();
        for (BatchBarcodeDTO.BarcodeGroup group : dto.getGroups()) {
            if (group.getTestItems() != null) {
                for (BatchBarcodeDTO.GroupTestItem ti : group.getTestItems()) {
                    if (ti.getOrderItemId() != null) {
                        requestOrderItemIds.add(ti.getOrderItemId());
                    }
                }
            }
        }
        if (!requestOrderItemIds.isEmpty()) {
            String idPh = requestOrderItemIds.stream().map(i -> "?").collect(Collectors.joining(","));
            List<Long> existing = jdbcTemplate.queryForList(
                    "SELECT order_item_id FROM lis_sample_order_item WHERE order_item_id IN (" + idPh + ")",
                    Long.class, requestOrderItemIds.toArray());
            if (!existing.isEmpty()) {
                throw new BusinessException("该就诊已生成条码, 不能重复生成, 如需重新生成请先取消已生成的条码");
            }
        }
        // 批量查询本次涉及申请的急诊状态 (visit_type==3), 用于传导标本 isUrgent
        Set<Long> allOrderIds = new HashSet<>();
        for (BatchBarcodeDTO.BarcodeGroup group : dto.getGroups()) {
            if (group.getTestItems() != null) {
                for (BatchBarcodeDTO.GroupTestItem ti : group.getTestItems()) {
                    if (ti.getOrderId() != null) {
                        allOrderIds.add(ti.getOrderId());
                    }
                }
            }
        }
        Set<Long> emergencyOrderIds = findEmergencyOrderIds(allOrderIds);

        LocalDateTime now = LocalDateTime.now();
        List<Sample> samples = new ArrayList<>(dto.getGroups().size());
        List<SampleOrderItem> relList = new ArrayList<>();
        for (BatchBarcodeDTO.BarcodeGroup group : dto.getGroups()) {
            Sample sample = new Sample();
            sample.setSampleBarcode(barcodeService.generateBarcode("SAMPLE"));
            // orderId 设为该组第一个项目的orderId (兼容旧逻辑, 实际关联通过中间表)
            if (group.getTestItems() != null && !group.getTestItems().isEmpty()) {
                sample.setOrderId(group.getTestItems().get(0).getOrderId());
            }
            sample.setPatientId(dto.getPatientId());
            sample.setPatientName(dto.getPatientName());
            sample.setVisitId(dto.getVisitId());
            sample.setVisitNo(dto.getVisitNo());
            sample.setSampleType(group.getSampleType() != null ? group.getSampleType() : "血清");
            sample.setContainerType(group.getContainerType() != null ? group.getContainerType() : "普通管");
            sample.setDepartment(dto.getDepartment());
            sample.setStatus(Sample.STATUS_APPLIED);
            // 急诊标记: 从申请单的 visit_type 传导 (该管任一申请为急诊则标本为急诊)
            boolean urgent = false;
            if (group.getTestItems() != null) {
                for (BatchBarcodeDTO.GroupTestItem ti : group.getTestItems()) {
                    if (ti.getOrderId() != null && emergencyOrderIds.contains(ti.getOrderId())) {
                        urgent = true;
                        break;
                    }
                }
            }
            sample.setIsUrgent(urgent ? 1 : 0);
            sample.setCreateTime(now);
            sample.setUpdateTime(now);
            samples.add(sample);
            // 收集标本-申请项目关联 (一个标本关联多个申请项目, 跨申请合管)
            if (group.getTestItems() != null) {
                for (BatchBarcodeDTO.GroupTestItem ti : group.getTestItems()) {
                    SampleOrderItem rel = new SampleOrderItem();
                    rel.setSampleId(sample.getId()); // saveBatch后会回填id
                    rel.setOrderId(ti.getOrderId());
                    rel.setOrderItemId(ti.getOrderItemId());
                    rel.setTestItemId(ti.getTestItemId());
                    rel.setCreateTime(now);
                    relList.add(rel);
                }
            }
        }
        // 批量插入标本 (单次SQL, id会回填到sample对象)
        this.saveBatch(samples);
        // 回填 sampleId 到关联记录
        int idx = 0;
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            BatchBarcodeDTO.BarcodeGroup group = dto.getGroups().get(i);
            int itemCount = group.getTestItems() != null ? group.getTestItems().size() : 0;
            for (int j = 0; j < itemCount; j++) {
                relList.get(idx + j).setSampleId(s.getId());
            }
            idx += itemCount;
        }
        // 批量插入标本-申请项目关联
        if (!relList.isEmpty()) {
            for (SampleOrderItem rel : relList) {
                sampleOrderItemMapper.insert(rel);
            }
        }
        // 异步发送MQ (不阻塞响应)
        for (Sample s : samples) {
            sendMqAsync("sample.created", s);
        }
        log.info("批量生成条码完成, 共{}管, visitId={}, 关联项目数={}", samples.size(), dto.getVisitId(), relList.size());
        // 直接构建轻量VO, 不调用toVO(避免N+1查询)
        return samples.stream().map(s -> {
            SampleVO vo = new SampleVO();
            BeanUtils.copyProperties(s, vo);
            vo.setStatusText(Sample.statusText(s.getStatus()));
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== 取消条码 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelBarcode(String barcode) {
        if (StringUtil.isEmpty(barcode)) {
            throw new BusinessException("标本条码不能为空");
        }
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sample::getSampleBarcode, barcode);
        Sample sample = this.getOne(wrapper);
        if (sample == null) {
            throw new BusinessException("标本不存在, 条码: " + barcode);
        }
        // 校验: 仅允许未采集(STATUS_APPLIED)的标本取消条码
        // 已采集/送检/签收/核收/报告/退费/作废/拒收的标本不可取消 (已进入流程, 条码号是唯一标识)
        if (sample.getStatus() != null && sample.getStatus() > Sample.STATUS_APPLIED) {
            throw new BusinessException("标本已" + Sample.statusText(sample.getStatus())
                    + ", 不可取消条码. 仅未采集的标本可取消条码");
        }
        // 1. 删除中间表关联 (lis_sample_order_item)
        LambdaQueryWrapper<SampleOrderItem> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(SampleOrderItem::getSampleId, sample.getId());
        sampleOrderItemMapper.delete(relWrapper);
        // 2. 删除标本 (逻辑删除或物理删除, 这里用物理删除以释放条码号)
        this.removeById(sample.getId());
        log.info("取消条码成功, barcode={}, sampleId={}", barcode, sample.getId());
    }
}
