package com.lis.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.report.entity.MicroAlert;
import com.lis.report.mapper.MicroAlertMapper;
import com.lis.report.service.MicroAlertService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微生物预警服务实现
 */
@Service
public class MicroAlertServiceImpl extends ServiceImpl<MicroAlertMapper, MicroAlert>
        implements MicroAlertService {

    @Override
    public List<MicroAlert> listAll(String status, String alertLevel) {
        LambdaQueryWrapper<MicroAlert> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(MicroAlert::getStatus, status);
        }
        if (alertLevel != null && !alertLevel.isEmpty()) {
            wrapper.eq(MicroAlert::getAlertLevel, alertLevel);
        }
        wrapper.orderByDesc(MicroAlert::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public List<MicroAlert> listByReportId(Long microReportId) {
        return this.list(new LambdaQueryWrapper<MicroAlert>()
                .eq(MicroAlert::getMicroReportId, microReportId)
                .orderByDesc(MicroAlert::getCreateTime));
    }

    @Override
    public MicroAlert create(MicroAlert alert) {
        if (alert.getStatus() == null) {
            alert.setStatus(MicroAlert.STATUS_PENDING);
        }
        if (alert.getCreateTime() == null) {
            alert.setCreateTime(LocalDateTime.now());
        }
        this.save(alert);
        return alert;
    }

    @Override
    public void markRead(Long id) {
        this.update(new LambdaUpdateWrapper<MicroAlert>()
                .eq(MicroAlert::getId, id)
                .set(MicroAlert::getStatus, MicroAlert.STATUS_READ));
    }

    @Override
    public void handle(Long id, String handler, String remark) {
        this.update(new LambdaUpdateWrapper<MicroAlert>()
                .eq(MicroAlert::getId, id)
                .set(MicroAlert::getStatus, MicroAlert.STATUS_HANDLED)
                .set(MicroAlert::getHandler, handler)
                .set(MicroAlert::getHandleRemark, remark)
                .set(MicroAlert::getHandleTime, LocalDateTime.now()));
    }
}
