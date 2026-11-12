package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.SupplierCreateDTO;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.Supplier;
import com.lis.reagent.enums.QualStatus;
import com.lis.reagent.mapper.ReagentInboundMapper;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderMapper;
import com.lis.reagent.mapper.SupplierMapper;
import com.lis.reagent.service.SupplierService;
import com.lis.reagent.vo.PriceCompareVO;
import com.lis.reagent.vo.SupplierPriceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商主数据服务实现。
 *
 * @author LIS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier>
        implements SupplierService {

    /** 供应商来源：1 手工录入（lis_supplier.source_type，DDL 定义） */
    private static final int SOURCE_TYPE_MANUAL = 1;

    private final SupplierMapper supplierMapper;
    private final ReagentMapper reagentMapper;
    private final ReagentInboundMapper inboundMapper;
    private final ReagentPurchaseOrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Supplier create(SupplierCreateDTO dto) {
        if (dto == null || dto.getSupplierName() == null || dto.getSupplierName().trim().isEmpty()) {
            throw new BusinessException("供应商名称不能为空");
        }
        if (dto.getSupplierCode() == null || dto.getSupplierCode().trim().isEmpty()) {
            throw new BusinessException("供应商编码不能为空");
        }
        checkCodeUnique(dto.getSupplierCode(), null);
        checkNameNormUnique(dto.getSupplierName(), null);

        Supplier supplier = new Supplier();
        supplier.setSupplierCode(dto.getSupplierCode().trim());
        supplier.setSupplierName(dto.getSupplierName().trim());
        supplier.setShortName(dto.getShortName());
        supplier.setCreditCode(dto.getCreditCode());
        supplier.setContactPerson(dto.getContactPerson());
        supplier.setPhone(dto.getPhone());
        supplier.setEmail(dto.getEmail());
        supplier.setAddress(dto.getAddress());
        supplier.setLicenseNo(dto.getLicenseNo());
        supplier.setLicenseExpiry(dto.getLicenseExpiry());
        supplier.setMedicalCertNo(dto.getMedicalCertNo());
        supplier.setMedicalCertExpiry(dto.getMedicalCertExpiry());
        supplier.setQualStatus(computeQualStatus(dto.getLicenseExpiry(), dto.getMedicalCertExpiry()));
        supplier.setSourceType(SOURCE_TYPE_MANUAL);
        supplier.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        supplier.setRemark(dto.getRemark());
        supplier.setCreateTime(LocalDateTime.now());
        supplier.setUpdateTime(LocalDateTime.now());
        this.save(supplier);
        log.info("新增供应商: code={}, name={}", supplier.getSupplierCode(), supplier.getSupplierName());
        return supplier;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Supplier update(SupplierCreateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException("供应商 ID 不能为空");
        }
        Supplier supplier = this.getById(dto.getId());
        if (supplier == null) {
            throw new BusinessException("供应商不存在: " + dto.getId());
        }
        if (dto.getSupplierName() != null && !dto.getSupplierName().trim().isEmpty()) {
            checkNameNormUnique(dto.getSupplierName(), dto.getId());
            supplier.setSupplierName(dto.getSupplierName().trim());
        }
        if (dto.getSupplierCode() != null && !dto.getSupplierCode().trim().isEmpty()) {
            checkCodeUnique(dto.getSupplierCode(), dto.getId());
            supplier.setSupplierCode(dto.getSupplierCode().trim());
        }
        supplier.setShortName(dto.getShortName());
        supplier.setCreditCode(dto.getCreditCode());
        supplier.setContactPerson(dto.getContactPerson());
        supplier.setPhone(dto.getPhone());
        supplier.setEmail(dto.getEmail());
        supplier.setAddress(dto.getAddress());
        supplier.setLicenseNo(dto.getLicenseNo());
        if (dto.getLicenseExpiry() != null) {
            supplier.setLicenseExpiry(dto.getLicenseExpiry());
        }
        supplier.setMedicalCertNo(dto.getMedicalCertNo());
        if (dto.getMedicalCertExpiry() != null) {
            supplier.setMedicalCertExpiry(dto.getMedicalCertExpiry());
        }
        supplier.setQualStatus(computeQualStatus(supplier.getLicenseExpiry(), supplier.getMedicalCertExpiry()));
        if (dto.getStatus() != null) {
            supplier.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            supplier.setRemark(dto.getRemark());
        }
        supplier.setUpdateTime(LocalDateTime.now());
        this.updateById(supplier);
        log.info("更新供应商: id={}, name={}", supplier.getId(), supplier.getSupplierName());
        return supplier;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Supplier supplier = this.getById(id);
        if (supplier == null) {
            throw new BusinessException("供应商不存在: " + id);
        }
        // 引用守卫：被试剂 / 入库单 / 订单引用时禁止物理删除（避免悬空外键），建议改为停用
        Long reagentRefs = reagentMapper.selectCount(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getSupplierId, id));
        Long inboundRefs = inboundMapper.selectCount(new LambdaQueryWrapper<com.lis.reagent.entity.ReagentInbound>()
                .eq(com.lis.reagent.entity.ReagentInbound::getSupplierId, id));
        Long orderRefs = orderMapper.selectCount(new LambdaQueryWrapper<com.lis.reagent.entity.ReagentPurchaseOrder>()
                .eq(com.lis.reagent.entity.ReagentPurchaseOrder::getSupplierId, id));
        if (reagentRefs + inboundRefs + orderRefs > 0) {
            throw new BusinessException("该供应商已被引用（试剂 "
                    + reagentRefs + " / 入库单 " + inboundRefs + " / 订单 " + orderRefs
                    + "），禁止删除；请改为停用（status=0）");
        }
        this.removeById(id);
        log.info("删除供应商: id={}, name={}", id, supplier.getSupplierName());
    }

    @Override
    public Page<Supplier> queryPage(long current, long size, String keyword, Integer status,
                                    Integer qualStatus, Integer sourceType) {
        LambdaQueryWrapper<Supplier> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            w.and(q -> q.like(Supplier::getSupplierName, kw)
                    .or().like(Supplier::getSupplierCode, kw)
                    .or().like(Supplier::getShortName, kw));
        }
        if (status != null) {
            w.eq(Supplier::getStatus, status);
        }
        if (qualStatus != null) {
            w.eq(Supplier::getQualStatus, qualStatus);
        }
        if (sourceType != null) {
            w.eq(Supplier::getSourceType, sourceType);
        }
        w.orderByDesc(Supplier::getCreateTime);
        return this.page(new Page<>(current, size), w);
    }

    @Override
    public List<Supplier> listSelectable() {
        return supplierMapper.selectSelectableSuppliers();
    }

    @Override
    public List<Supplier> listExpiringWithin(int days) {
        return supplierMapper.licenseExpiryWithinDays(days);
    }

    @Override
    public PriceCompareVO comparePrice(Long reagentId) {
        Reagent reagent = reagentMapper.selectById(reagentId);
        PriceCompareVO vo = new PriceCompareVO();
        if (reagent != null) {
            vo.setReagentId(reagent.getId());
            vo.setReagentCode(reagent.getReagentCode());
            vo.setReagentName(reagent.getReagentName());
            vo.setSpecification(reagent.getSpecification());
        } else {
            vo.setReagentId(reagentId);
        }
        List<SupplierPriceVO> suppliers = supplierMapper.comparePriceByReagent(reagentId);
        vo.setSuppliers(suppliers);
        if (!suppliers.isEmpty()) {
            // 已按 avg_price 升序
            SupplierPriceVO lowest = suppliers.get(0);
            vo.setLowestPrice(lowest.getAvgPrice());
            vo.setLowestSupplierId(lowest.getSupplierId());
            vo.setLowestSupplierName(lowest.getSupplierName());
        }
        return vo;
    }

    /**
     * 供应商编码唯一性校验。
     */
    private void checkCodeUnique(String code, Long excludeId) {
        Long existed = this.count(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, code.trim())
                .ne(excludeId != null, Supplier::getId, excludeId));
        if (existed > 0) {
            throw new BusinessException("供应商编码已存在: " + code.trim());
        }
    }

    /**
     * 规范化名称重名校验（与 DB 函数 fn_supplier_name_norm 同口径）。
     */
    private void checkNameNormUnique(String name, Long excludeId) {
        int dup = supplierMapper.countByNameNorm(name, excludeId);
        if (dup > 0) {
            throw new BusinessException("供应商名称已存在（名称归一化后重复）: " + name.trim());
        }
    }

    /**
     * 计算资质状态：两证均未填 → 待完善；任一已过期 → 已过期；否则有效。
     */
    private int computeQualStatus(LocalDate licenseExpiry, LocalDate medicalCertExpiry) {
        if (licenseExpiry == null && medicalCertExpiry == null) {
            return QualStatus.PENDING.getCode();
        }
        LocalDate today = LocalDate.now();
        if ((licenseExpiry != null && licenseExpiry.isBefore(today))
                || (medicalCertExpiry != null && medicalCertExpiry.isBefore(today))) {
            return QualStatus.EXPIRED.getCode();
        }
        return QualStatus.VALID.getCode();
    }
}
