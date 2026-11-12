package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.Bacteria;

import java.util.List;

public interface BacteriaService extends IService<Bacteria> {

    Page<Bacteria> queryPage(long current, long size, String keyword, String gramType, String category);

    Bacteria getByCode(String bacteriaCode);

    List<Bacteria> listAllEnabled();

    List<Bacteria> listCommon();
}
