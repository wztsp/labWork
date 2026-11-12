package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.TitleDict;
import com.lis.hr.mapper.TitleDictMapper;
import com.lis.hr.service.TitleDictService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TitleDictServiceImpl extends ServiceImpl<TitleDictMapper, TitleDict> implements TitleDictService {

    @Override
    public List<TitleDict> listAll() {
        return this.list(new LambdaQueryWrapper<TitleDict>()
                .orderByAsc(TitleDict::getSortOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TitleDict create(TitleDict titleDict) {
        if (titleDict.getTitleCode() == null || titleDict.getTitleCode().isEmpty()) {
            throw new BusinessException("职称编码不能为空");
        }
        if (titleDict.getTitleName() == null || titleDict.getTitleName().isEmpty()) {
            throw new BusinessException("职称名称不能为空");
        }
        if (titleDict.getStatus() == null) {
            titleDict.setStatus(1);
        }
        if (titleDict.getSortOrder() == null) {
            titleDict.setSortOrder(0);
        }
        this.save(titleDict);
        return titleDict;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TitleDict update(TitleDict titleDict) {
        if (titleDict.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(titleDict);
        return titleDict;
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }
}
