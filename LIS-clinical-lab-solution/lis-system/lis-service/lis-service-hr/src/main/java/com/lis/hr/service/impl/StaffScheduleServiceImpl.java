package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.Staff;
import com.lis.hr.entity.StaffSchedule;
import com.lis.hr.mapper.StaffMapper;
import com.lis.hr.mapper.StaffScheduleMapper;
import com.lis.hr.service.StaffScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StaffScheduleServiceImpl extends ServiceImpl<StaffScheduleMapper, StaffSchedule>
        implements StaffScheduleService {

    @Autowired
    private StaffMapper staffMapper;

    @Override
    public List<StaffSchedule> listByDate(LocalDate date) {
        return this.list(new LambdaQueryWrapper<StaffSchedule>()
                .eq(StaffSchedule::getScheduleDate, date)
                .orderByAsc(StaffSchedule::getStaffId));
    }

    @Override
    public List<StaffSchedule> listByStaffId(Long staffId) {
        return this.list(new LambdaQueryWrapper<StaffSchedule>()
                .eq(StaffSchedule::getStaffId, staffId)
                .orderByDesc(StaffSchedule::getScheduleDate));
    }

    @Override
    public List<StaffSchedule> listByRange(LocalDate start, LocalDate end) {
        return this.list(new LambdaQueryWrapper<StaffSchedule>()
                .ge(StaffSchedule::getScheduleDate, start)
                .le(StaffSchedule::getScheduleDate, end)
                .orderByAsc(StaffSchedule::getScheduleDate)
                .orderByAsc(StaffSchedule::getStaffId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignBatch(List<Long> staffIds, List<LocalDate> dates, String shiftType, String dutyType) {
        if (staffIds == null || staffIds.isEmpty()) {
            throw new BusinessException("请至少选择一名人员");
        }
        if (dates == null || dates.isEmpty()) {
            throw new BusinessException("请至少选择一个日期");
        }
        int count = 0;
        for (Long staffId : staffIds) {
            for (LocalDate date : dates) {
                StaffSchedule existing = this.getOne(new LambdaQueryWrapper<StaffSchedule>()
                        .eq(StaffSchedule::getStaffId, staffId)
                        .eq(StaffSchedule::getScheduleDate, date), false);
                if (existing != null) {
                    existing.setShiftType(shiftType);
                    existing.setDutyType(dutyType);
                    this.updateById(existing);
                } else {
                    StaffSchedule schedule = new StaffSchedule();
                    schedule.setStaffId(staffId);
                    schedule.setScheduleDate(date);
                    schedule.setShiftType(shiftType);
                    schedule.setDutyType(dutyType);
                    schedule.setCreateTime(LocalDateTime.now());
                    this.save(schedule);
                }
                count++;
            }
        }
        return count;
    }

    @Override
    public void page(Page<StaffSchedule> page, String staffName, LocalDate scheduleDate, String shiftType) {
        LambdaQueryWrapper<StaffSchedule> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(staffName)) {
            List<Long> staffIds = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                            .select(Staff::getId)
                            .like(Staff::getName, staffName))
                    .stream().map(Staff::getId).collect(Collectors.toList());
            if (staffIds.isEmpty()) {
                wrapper.eq(StaffSchedule::getId, -1L);
            } else {
                wrapper.in(StaffSchedule::getStaffId, staffIds);
            }
        }
        if (scheduleDate != null) {
            wrapper.eq(StaffSchedule::getScheduleDate, scheduleDate);
        }
        if (StringUtils.hasText(shiftType)) {
            wrapper.eq(StaffSchedule::getShiftType, shiftType);
        }
        wrapper.orderByDesc(StaffSchedule::getScheduleDate);
        this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffSchedule create(StaffSchedule schedule) {
        if (schedule.getStaffId() == null) {
            throw new BusinessException("人员ID不能为空");
        }
        if (schedule.getScheduleDate() == null) {
            throw new BusinessException("排班日期不能为空");
        }
        schedule.setCreateTime(LocalDateTime.now());
        this.save(schedule);
        return schedule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffSchedule update(StaffSchedule schedule) {
        if (schedule.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(schedule);
        return schedule;
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }
}
