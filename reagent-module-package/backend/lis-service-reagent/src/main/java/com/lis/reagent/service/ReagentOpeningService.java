package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.reagent.entity.ReagentOpening;

public interface ReagentOpeningService extends IService<ReagentOpening> {

    /** 开瓶登记: 计算开瓶效期 */
    ReagentOpening createOpening(ReagentOpening opening);

    /** 锁定已过开瓶效期的记录 */
    int lockExpiredOpenings();

    /** 分页查询 */
    Page<ReagentOpening> queryPage(long current, long size, Long reagentId, Integer status);
}
