package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.micro.entity.Antibiotic;
import com.lis.micro.mapper.AntibioticMapper;
import com.lis.micro.service.AntibioticService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AntibioticServiceImpl extends ServiceImpl<AntibioticMapper, Antibiotic> implements AntibioticService {

    @Override
    public Page<Antibiotic> queryPage(long current, long size, String keyword, String category) {
        LambdaQueryWrapper<Antibiotic> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Antibiotic::getAntibioticCode, keyword)
                    .or().like(Antibiotic::getAntibioticName, keyword));
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Antibiotic::getCategory, category);
        }
        wrapper.orderByAsc(Antibiotic::getCategory, Antibiotic::getAntibioticCode);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public Antibiotic getByCode(String antibioticCode) {
        return this.getOne(new LambdaQueryWrapper<Antibiotic>()
                .eq(Antibiotic::getAntibioticCode, antibioticCode));
    }

    @Override
    public List<Antibiotic> listAllEnabled() {
        return this.list(new LambdaQueryWrapper<Antibiotic>().eq(Antibiotic::getStatus, 1));
    }
}
