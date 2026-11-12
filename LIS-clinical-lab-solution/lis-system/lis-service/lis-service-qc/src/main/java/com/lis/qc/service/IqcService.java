package com.lis.qc.service;

import java.util.List;
import java.util.Map;

/**
 * 室内质控(IQC)服务: 运行概览KPI、质控项目列表(状态/最近值)、项目详情。
 * 依据 iqc-module-design.md。
 */
public interface IqcService {

    List<Map<String, Object>> projectList(Long instrumentId, String group, String status, String keyword);

    Map<String, Object> summary();

    Map<String, Object> projectDetail(Long lotId);
}