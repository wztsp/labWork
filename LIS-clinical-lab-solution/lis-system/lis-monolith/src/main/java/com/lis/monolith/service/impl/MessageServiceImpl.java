package com.lis.monolith.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.security.SecurityContext;
import com.lis.monolith.entity.Message;
import com.lis.monolith.mapper.MessageMapper;
import com.lis.monolith.mapper.TatDataMapper;
import com.lis.monolith.service.MessageService;
import com.lis.system.entity.SysUser;
import com.lis.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消息提醒服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final TatDataMapper tatDataMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public void broadcast(String type, String title, String content, Long businessId, Long sampleId, Long testItemId) {
        insert(type, title, content, null, null, businessId, sampleId, testItemId);
    }

    @Override
    public void sendToDepartment(Long departmentId, String type, String title, String content, Long businessId, Long sampleId, Long testItemId) {
        insert(type, title, content, null, departmentId, businessId, sampleId, testItemId);
    }

    @Override
    public void sendToUser(Long userId, String type, String title, String content, Long businessId, Long sampleId, Long testItemId) {
        insert(type, title, content, userId, null, businessId, sampleId, testItemId);
    }

    private void insert(String type, String title, String content, Long userId, Long deptId,
                        Long businessId, Long sampleId, Long testItemId) {
        Message m = new Message();
        m.setMessageType(type);
        m.setTitle(title);
        m.setContent(content);
        m.setUserId(userId);
        m.setDepartmentId(deptId);
        m.setBusinessId(businessId);
        m.setSampleId(sampleId);
        m.setTestItemId(testItemId);
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
    }

    @Override
    public IPage<Message> pageForUser(Long userId, Long deptId, String type, int page, int size) {
        Page<Message> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        IPage<Message> result = tatDataMapper.pageMessages(p, userId, deptId);
        if (type != null && !type.isBlank()) {
            // 内存过滤消息类型 (分页结果上过滤)
            result.getRecords().removeIf(m -> !type.equals(m.getMessageType()));
        }
        return result;
    }

    @Override
    public long unreadCount(Long userId, Long deptId) {
        return tatDataMapper.countUnread(userId, deptId);
    }

    @Override
    public void markRead(Long id, Long userId, Long deptId) {
        Message m = visible(id, userId, deptId);
        if (m == null) {
            return;
        }
        LambdaUpdateWrapper<Message> uw = new LambdaUpdateWrapper<>();
        uw.eq(Message::getId, id).set(Message::getIsRead, 1).set(Message::getReadTime, LocalDateTime.now());
        messageMapper.update(null, uw);
    }

    @Override
    public void markAllRead(Long userId, Long deptId) {
        LambdaUpdateWrapper<Message> uw = new LambdaUpdateWrapper<>();
        uw.and(w -> w.eq(Message::getUserId, userId)
                .or(n -> n.isNull(Message::getUserId)
                        .and(c -> c.isNull(Message::getDepartmentId)
                                .or().eq(Message::getDepartmentId, deptId))))
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1)
                .set(Message::getReadTime, LocalDateTime.now());
        messageMapper.update(null, uw);
    }

    @Override
    public void deleteMessage(Long id, Long userId, Long deptId) {
        Message m = visible(id, userId, deptId);
        if (m == null) {
            return;
        }
        messageMapper.deleteById(id);
    }

    private Message visible(Long id, Long userId, Long deptId) {
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<>();
        qw.eq(Message::getId, id)
                .and(w -> w.eq(Message::getUserId, userId)
                        .or(n -> n.isNull(Message::getUserId)
                                .and(c -> c.isNull(Message::getDepartmentId)
                                        .or().eq(Message::getDepartmentId, deptId))));
        return messageMapper.selectOne(qw);
    }

    @Override
    public Long currentUserId() {
        return SecurityContext.getUserId();
    }

    @Override
    public Long currentDeptId() {
        Long userId = currentUserId();
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? user.getDepartmentId() : null;
    }
}