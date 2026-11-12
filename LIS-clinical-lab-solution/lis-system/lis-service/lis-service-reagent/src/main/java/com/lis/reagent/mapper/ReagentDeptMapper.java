package com.lis.reagent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 试剂模块科室选项查询。
 *
 * <p>独立部署时不包含 system 模块的 /api/system/departments，
 * 因此从试剂自身的业务表（预算、采购申请、业务单）中抽取已使用过的科室，
 * 供前端下拉选择。主系统与独立版共用同一接口。</p>
 *
 * <p>同一科室名可能存在多个历史 ID（不同来源快照），按名称去重并优先保留
 * 预算表中的 ID，确保选择后能命中预算；名称为空的脏数据不返回。</p>
 */
@Mapper
public interface ReagentDeptMapper {

    @Select("SELECT DISTINCT ON (\"deptName\") id, \"deptName\" FROM ( " +
            "  SELECT dept_id AS id, dept_name AS \"deptName\", 1 AS pri " +
            "    FROM lis_reagent_budget " +
            "   WHERE dept_id IS NOT NULL AND dept_name IS NOT NULL AND dept_name <> '' " +
            "  UNION ALL " +
            "  SELECT apply_dept_id AS id, apply_dept_name AS \"deptName\", 2 AS pri " +
            "    FROM lis_reagent_purchase_apply " +
            "   WHERE apply_dept_id IS NOT NULL AND apply_dept_name IS NOT NULL AND apply_dept_name <> '' " +
            "  UNION ALL " +
            "  SELECT dept_id AS id, dept_name AS \"deptName\", 3 AS pri " +
            "    FROM lis_reagent_requisition " +
            "   WHERE dept_id IS NOT NULL AND dept_name IS NOT NULL AND dept_name <> '' " +
            ") t ORDER BY \"deptName\", pri, id")
    List<Map<String, Object>> selectDeptOptions();
}
