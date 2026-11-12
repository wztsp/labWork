package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.StaffSchedule;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班服务
 */
public interface StaffScheduleService extends IService<StaffSchedule> {

    List<StaffSchedule> listByDate(LocalDate date);

    List<StaffSchedule> listByStaffId(Long staffId);

    /** 查询某日期区间内的所有排班 (排班表按周加载用) */
    List<StaffSchedule> listByRange(LocalDate start, LocalDate end);

    /** 批量指定班次: 对 多个员工 × 多个日期 生成/更新排班, 返回记录数 */
    int assignBatch(List<Long> staffIds, List<LocalDate> dates, String shiftType, String dutyType);

    void page(Page<StaffSchedule> page, String staffName, LocalDate scheduleDate, String shiftType);

    StaffSchedule create(StaffSchedule schedule);

    StaffSchedule update(StaffSchedule schedule);

    void delete(Long id);
}
