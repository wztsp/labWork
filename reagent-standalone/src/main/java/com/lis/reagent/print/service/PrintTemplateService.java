package com.lis.reagent.print.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.reagent.print.entity.PrintTemplate;

import java.util.List;

/**
 * 打印模板服务。
 *
 * @author LIS Team
 */
public interface PrintTemplateService {

    /** 分页查询模板 */
    Page<PrintTemplate> queryPage(long current, long size, String bizType, String keyword, Integer status);

    /** 查询某业务类型下启用的模板（按默认优先、更新时间倒序） */
    List<PrintTemplate> listEnabled(String bizType);

    /** 详情 */
    PrintTemplate getDetail(Long id);

    /** 新增或更新（含默认模板唯一性处理） */
    PrintTemplate saveTemplate(PrintTemplate template);

    /** 删除（默认模板不可删除） */
    void delete(Long id);

    /** 设为该业务类型的默认模板 */
    void setDefault(Long id);

    /** 取默认模板，无默认时取该业务类型第一个启用模板；都没有返回 null */
    PrintTemplate getDefaultOrFirst(String bizType);
}
