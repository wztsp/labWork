package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.print.entity.PrintTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 打印模板 Mapper。
 *
 * <p>注意：必须放在 {@code com.lis.reagent.mapper} 下，应用启动类
 * {@code @MapperScan("com.lis.reagent.mapper")} 才会扫描到。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface PrintTemplateMapper extends BaseMapper<PrintTemplate> {
}
