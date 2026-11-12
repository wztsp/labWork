package com.lis.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.base.entity.Instrument;
import com.lis.base.entity.InstrumentItemMap;
import com.lis.base.entity.InstrumentSpecimenMap;
import com.lis.base.entity.InstrumentStaffMap;

import java.util.List;
import java.util.Set;

/**
 * 仪器 Service
 */
public interface InstrumentService extends IService<Instrument> {

    /**
     * 分页查询仪器
     */
    Page<Instrument> queryPage(long current, long size, String keyword);

    /**
     * 查询全部启用的仪器 (下拉选择用)
     */
    List<Instrument> listAllEnabled();

    /**
     * 查询当前登录员工可操作的启用仪器
     */
    List<Instrument> listMyInstruments(Long userId);

    /**
     * 根据ID查询
     */
    Instrument getDetail(Long id);

    /**
     * 新增
     */
    Instrument create(Instrument instrument);

    /**
     * 更新
     */
    Instrument update(Instrument instrument);

    /**
     * 删除
     */
    void delete(Long id);

    /**
     * 查询仪器项目映射
     */
    List<InstrumentItemMap> listItemMaps(Long instrumentId);

    /**
     * 批量设置仪器项目映射
     */
    void setItemMaps(Long instrumentId, List<InstrumentItemMap> maps);

    /**
     * 查询仪器关联的操作人员ID集合
     */
    Set<Long> listStaffIds(Long instrumentId);

    /**
     * 查询仪器关联的操作人员列表
     */
    List<InstrumentStaffMap> listStaffMaps(Long instrumentId);

    /**
     * 设置仪器关联的操作人员（覆盖式）
     */
    void setStaffMaps(Long instrumentId, List<InstrumentStaffMap> staffMaps);

    /** 查询仪器关联的标本类型列表 */
    List<InstrumentSpecimenMap> listSpecimenMaps(Long instrumentId);

    /** 设置仪器关联的标本类型（覆盖式） */
    void setSpecimenMaps(Long instrumentId, List<InstrumentSpecimenMap> specimenMaps);
}
