package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.Staff;
import com.lis.hr.entity.StaffCertificate;
import com.lis.hr.mapper.StaffCertificateMapper;
import com.lis.hr.mapper.StaffMapper;
import com.lis.hr.service.AccountRevokeService;
import com.lis.hr.service.StaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffServiceImpl extends ServiceImpl<StaffMapper, Staff> implements StaffService {

    private final StaffCertificateMapper staffCertificateMapper;
    private final AccountRevokeService accountRevokeService;

    @Override
    public Page<Staff> queryPage(long current, long size, String keyword, Long groupId, Integer status) {
        LambdaQueryWrapper<Staff> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Staff::getName, keyword)
                    .or().like(Staff::getStaffNo, keyword)
                    .or().like(Staff::getPhone, keyword));
        }
        if (groupId != null) {
            wrapper.eq(Staff::getGroupId, groupId);
        }
        if (status != null) {
            wrapper.eq(Staff::getStatus, status);
        }
        wrapper.orderByDesc(Staff::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public Staff getByStaffNo(String staffNo) {
        return this.getOne(new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, staffNo));
    }

    @Override
    public List<Staff> listByGroupId(Long groupId) {
        return this.list(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getGroupId, groupId)
                .eq(Staff::getStatus, 1)
                .orderByAsc(Staff::getStaffNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Staff create(Staff staff) {
        if (staff.getStaffNo() == null || staff.getStaffNo().isEmpty()) {
            throw new BusinessException("工号不能为空");
        }
        if (this.getByStaffNo(staff.getStaffNo()) != null) {
            throw new BusinessException("工号已存在: " + staff.getStaffNo());
        }
        if (staff.getStatus() == null) {
            staff.setStatus(1);
        }
        LocalDateTime now = LocalDateTime.now();
        staff.setCreateTime(now);
        staff.setUpdateTime(now);
        this.save(staff);
        return staff;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Staff update(Staff staff) {
        if (staff.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        staff.setUpdateTime(LocalDateTime.now());
        this.updateById(staff);
        return staff;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resign(Long id) {
        Staff staff = this.getById(id);
        if (staff == null) {
            throw new BusinessException("人员不存在: " + id);
        }
        staff.setStatus(0);
        staff.setLeaveDate(LocalDate.now());
        staff.setUpdateTime(LocalDateTime.now());
        this.updateById(staff);
        // B-4: 联动停用系统账号 (同 @Transactional 内同库直写, 由 AccountRevokeService 接口隔离)
        if (staff.getUserId() != null) {
            try {
                accountRevokeService.revoke(staff.getUserId(), "离职");
            } catch (Exception e) {
                // 账号撤销失败不得阻断离职主流程 (revoke 内部已降级, 此处为双保险)
                log.warn("离职联动停用系统账号失败, staffId={}, userId={}", id, staff.getUserId(), e);
            }
        }
        // B-4 Tier-2: 离职即刻踢下线 —— 已由认证维度实现:
        //  撤销时写入 USER_REVOKED:{uid}=撤销时刻毫秒戳(24h TTL),
        //  认证过滤器 TokenAuthenticationFilter#isRevoked 按 JWT iat 拒绝撤销前签发的旧 token。
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }

    @Override
    public List<Staff> listStaffWithExpiringCertificates(int withinDays) {
        LocalDate now = LocalDate.now();
        LocalDate deadline = now.plusDays(withinDays);
        // 查询即将到期的证书
        List<StaffCertificate> certs = staffCertificateMapper.selectList(
                new LambdaQueryWrapper<StaffCertificate>()
                        .eq(StaffCertificate::getStatus, 1)
                        .between(StaffCertificate::getExpiryDate, now, deadline));
        if (certs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> staffIds = certs.stream().map(StaffCertificate::getStaffId).distinct().collect(Collectors.toList());
        return this.listByIds(staffIds);
    }
}
