package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.dto.AntibioticTemplateApplyVO;
import com.lis.micro.dto.AntibioticTemplateSaveRequest;
import com.lis.micro.dto.AntibioticTemplateVO;
import com.lis.micro.entity.AntibioticTemplate;

import java.util.List;

/**
 * 抗生素药敏模板 Service
 */
public interface AntibioticTemplateService extends IService<AntibioticTemplate> {

    Page<AntibioticTemplateVO> queryPage(long current, long size, String keyword, String category, Integer status);

    /** 按细菌字典ID查询可用的启用模板（供报告页下拉），已按 isSystem/sortNo 排序 */
    List<AntibioticTemplate> listEnabledByBacteria(Long bacteriaId);

    /** 模板详情（含绑定细菌 + 抗生素明细） */
    AntibioticTemplateVO getDetail(Long id);

    /** 构造「应用模板」数据：针对指定细菌解析每项抗生素的字典信息与折点 */
    AntibioticTemplateApplyVO buildApply(Long id, Long bacteriaId);

    /** 新增或更新模板（主表 + 明细 + 细菌绑定，事务内整体替换） */
    Long saveOrUpdate(AntibioticTemplateSaveRequest req);

    /** 删除模板（系统内置模板不允许删除）；级联删除明细与细菌绑定 */
    void deleteById(Long id);
}