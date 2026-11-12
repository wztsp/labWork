package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.Antibiotic;

import java.util.List;

public interface AntibioticService extends IService<Antibiotic> {

    Page<Antibiotic> queryPage(long current, long size, String keyword, String category);

    Antibiotic getByCode(String antibioticCode);

    List<Antibiotic> listAllEnabled();
}
