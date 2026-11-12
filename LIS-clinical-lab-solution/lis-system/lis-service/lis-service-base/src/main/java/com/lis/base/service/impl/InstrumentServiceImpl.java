package com.lis.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.base.entity.Instrument;
import com.lis.base.entity.InstrumentItemMap;
import com.lis.base.entity.InstrumentSpecimenMap;
import com.lis.base.entity.InstrumentStaffMap;
import com.lis.base.mapper.BaseInstrumentItemMapMapper;
import com.lis.base.mapper.BaseInstrumentMapper;
import com.lis.base.mapper.InstrumentSpecimenMapMapper;
import com.lis.base.mapper.InstrumentStaffMapMapper;
import com.lis.base.service.InstrumentService;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.util.StringUtil;
import com.lis.hr.entity.Staff;
import com.lis.hr.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仪器 Service 实现
 */
@Service
@RequiredArgsConstructor
public class InstrumentServiceImpl extends ServiceImpl<BaseInstrumentMapper, Instrument> implements InstrumentService {

    private final BaseInstrumentItemMapMapper instrumentItemMapMapper;
    private final InstrumentStaffMapMapper instrumentStaffMapMapper;
    private final StaffMapper staffMapper;
    private final InstrumentSpecimenMapMapper instrumentSpecimenMapMapper;

    @Override
    public Page<Instrument> queryPage(long current, long size, String keyword) {
        LambdaQueryWrapper<Instrument> wrapper = new LambdaQueryWrapper<>();
        if (StringUtil.isNotEmpty(keyword)) {
            wrapper.and(w -> w.like(Instrument::getInstrumentCode, keyword)
                    .or().like(Instrument::getInstrumentName, keyword)
                    .or().like(Instrument::getBrand, keyword));
        }
        wrapper.orderByDesc(Instrument::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public List<Instrument> listAllEnabled() {
        LambdaQueryWrapper<Instrument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Instrument::getStatus, 1);
        wrapper.orderByDesc(Instrument::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public List<Instrument> listMyInstruments(Long userId) {
        // 1. 根据 userId 查出员工
        if (userId == null) {
            return listAllEnabled();
        }
        Staff staff;
        try {
            LambdaQueryWrapper<Staff> sw = new LambdaQueryWrapper<>();
            sw.eq(Staff::getUserId, userId).eq(Staff::getStatus, 1).last("LIMIT 1");
            staff = staffMapper.selectOne(sw);
        } catch (Exception e) {
            // HR mapper 不可用（模块未启动等）, 回退到返回全部仪器
            return listAllEnabled();
        }
        if (staff == null) {
            // 未绑定员工的用户: 返回全部（避免阻断）
            return listAllEnabled();
        }
        // 2. 通过关联表查 instrumentIds
        LambdaQueryWrapper<InstrumentStaffMap> swm = new LambdaQueryWrapper<>();
        swm.eq(InstrumentStaffMap::getStaffId, staff.getId());
        List<InstrumentStaffMap> maps = instrumentStaffMapMapper.selectList(swm);
        if (maps == null || maps.isEmpty()) {
            // 该员工还未分配任何仪器：返回全部（管理员场景或未配置）
            return listAllEnabled();
        }
        Set<Long> instrumentIds = maps.stream()
                .map(InstrumentStaffMap::getInstrumentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        // 3. 查仪器
        LambdaQueryWrapper<Instrument> iw = new LambdaQueryWrapper<>();
        iw.eq(Instrument::getStatus, 1).in(Instrument::getId, instrumentIds);
        iw.orderByDesc(Instrument::getCreateTime);
        return this.list(iw);
    }

    @Override
    public Instrument getDetail(Long id) {
        return this.getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Instrument create(Instrument instrument) {
        if (instrument.getStatus() == null) {
            instrument.setStatus(1);
        }
        instrument.setCreateTime(LocalDateTime.now());
        this.save(instrument);
        return instrument;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Instrument update(Instrument instrument) {
        if (instrument.getId() == null) {
            throw new BusinessException("仪器ID不能为空");
        }
        this.updateById(instrument);
        return instrument;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
        // 同步删除项目映射
        LambdaQueryWrapper<InstrumentItemMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstrumentItemMap::getInstrumentId, id);
        instrumentItemMapMapper.delete(wrapper);
        // 同步删除人员映射
        LambdaQueryWrapper<InstrumentStaffMap> sw = new LambdaQueryWrapper<>();
        sw.eq(InstrumentStaffMap::getInstrumentId, id);
        instrumentStaffMapMapper.delete(sw);
        // 同步删除标本类型映射
        LambdaQueryWrapper<InstrumentSpecimenMap> spw = new LambdaQueryWrapper<>();
        spw.eq(InstrumentSpecimenMap::getInstrumentId, id);
        instrumentSpecimenMapMapper.delete(spw);
    }

    @Override
    public List<InstrumentItemMap> listItemMaps(Long instrumentId) {
        LambdaQueryWrapper<InstrumentItemMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstrumentItemMap::getInstrumentId, instrumentId);
        return instrumentItemMapMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setItemMaps(Long instrumentId, List<InstrumentItemMap> maps) {
        // 先删除原有映射
        LambdaQueryWrapper<InstrumentItemMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstrumentItemMap::getInstrumentId, instrumentId);
        instrumentItemMapMapper.delete(wrapper);
        // 批量插入新映射
        if (maps != null && !maps.isEmpty()) {
            maps.forEach(map -> {
                map.setId(null);
                map.setInstrumentId(instrumentId);
                instrumentItemMapMapper.insert(map);
            });
        }
    }

    @Override
    public Set<Long> listStaffIds(Long instrumentId) {
        if (instrumentId == null) return Collections.emptySet();
        LambdaQueryWrapper<InstrumentStaffMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstrumentStaffMap::getInstrumentId, instrumentId);
        List<InstrumentStaffMap> list = instrumentStaffMapMapper.selectList(wrapper);
        return list.stream()
                .map(InstrumentStaffMap::getStaffId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public List<InstrumentStaffMap> listStaffMaps(Long instrumentId) {
        if (instrumentId == null) return Collections.emptyList();
        LambdaQueryWrapper<InstrumentStaffMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstrumentStaffMap::getInstrumentId, instrumentId);
        return instrumentStaffMapMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setStaffMaps(Long instrumentId, List<InstrumentStaffMap> staffMaps) {
        // 先删除原有映射
        LambdaQueryWrapper<InstrumentStaffMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstrumentStaffMap::getInstrumentId, instrumentId);
        instrumentStaffMapMapper.delete(wrapper);
        // 批量插入
        if (staffMaps != null && !staffMaps.isEmpty()) {
            staffMaps.forEach(map -> {
                map.setId(null);
                map.setInstrumentId(instrumentId);
                instrumentStaffMapMapper.insert(map);
            });
        }
    }

    @Override
    public List<InstrumentSpecimenMap> listSpecimenMaps(Long instrumentId) {
        if (instrumentId == null) return Collections.emptyList();
        LambdaQueryWrapper<InstrumentSpecimenMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstrumentSpecimenMap::getInstrumentId, instrumentId);
        return instrumentSpecimenMapMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setSpecimenMaps(Long instrumentId, List<InstrumentSpecimenMap> specimenMaps) {
        // 先删除原有映射
        LambdaQueryWrapper<InstrumentSpecimenMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstrumentSpecimenMap::getInstrumentId, instrumentId);
        instrumentSpecimenMapMapper.delete(wrapper);
        // 批量插入
        if (specimenMaps != null && !specimenMaps.isEmpty()) {
            specimenMaps.forEach(map -> {
                map.setId(null);
                map.setInstrumentId(instrumentId);
                instrumentSpecimenMapMapper.insert(map);
            });
        }
    }
}
