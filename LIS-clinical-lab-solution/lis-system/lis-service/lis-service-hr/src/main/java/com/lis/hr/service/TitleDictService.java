package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.TitleDict;

import java.util.List;

/**
 * 职称字典服务
 */
public interface TitleDictService extends IService<TitleDict> {

    List<TitleDict> listAll();

    TitleDict create(TitleDict titleDict);

    TitleDict update(TitleDict titleDict);

    void delete(Long id);
}
