package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.micro.entity.Bacteria;
import com.lis.micro.mapper.BacteriaMapper;
import com.lis.micro.service.BacteriaService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BacteriaServiceImpl extends ServiceImpl<BacteriaMapper, Bacteria> implements BacteriaService {

    @Override
    public Page<Bacteria> queryPage(long current, long size, String keyword, String gramType, String category) {
        LambdaQueryWrapper<Bacteria> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Bacteria::getBacteriaCode, keyword)
                    .or().like(Bacteria::getBacteriaName, keyword)
                    .or().like(Bacteria::getBacteriaNameLatin, keyword));
        }
        if (gramType != null && !gramType.isEmpty()) {
            wrapper.eq(Bacteria::getGramType, gramType);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Bacteria::getBacteriaCategory, category);
        }
        wrapper.orderByDesc(Bacteria::getIsCommon).orderByAsc(Bacteria::getBacteriaCategory, Bacteria::getBacteriaCode);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public Bacteria getByCode(String bacteriaCode) {
        return this.getOne(new LambdaQueryWrapper<Bacteria>()
                .eq(Bacteria::getBacteriaCode, bacteriaCode));
    }

    @Override
    public List<Bacteria> listAllEnabled() {
        return this.list(new LambdaQueryWrapper<Bacteria>().eq(Bacteria::getStatus, 1));
    }

    @Override
    public List<Bacteria> listCommon() {
        return this.list(new LambdaQueryWrapper<Bacteria>()
                .eq(Bacteria::getStatus, 1)
                .eq(Bacteria::getIsCommon, 1));
    }
}
