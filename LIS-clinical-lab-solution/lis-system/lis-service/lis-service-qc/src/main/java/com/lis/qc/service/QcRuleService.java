package com.lis.qc.service;

import com.lis.qc.entity.QcRule;

import java.util.List;

/**
 * Westgard 规则配置服务
 */
public interface QcRuleService {

    List<QcRule> listAll();

    List<QcRule> listEnabled();

    QcRule getById(Long id);

    Long create(QcRule rule);

    boolean update(QcRule rule);

    boolean delete(Long id);

    /** 启停规则 */
    boolean toggleEnabled(Long id, int enabled);
}
