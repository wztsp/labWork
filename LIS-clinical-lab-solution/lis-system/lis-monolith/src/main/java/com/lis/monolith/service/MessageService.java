package com.lis.monolith.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lis.monolith.entity.Message;

import java.util.Map;

/**
 * 消息提醒服务
 */
public interface MessageService {

    /** 全员广播 */
    void broadcast(String type, String title, String content, Long businessId, Long sampleId, Long testItemId);

    /** 发送给指定科室 (科室下所有用户可见) */
    void sendToDepartment(Long departmentId, String type, String title, String content, Long businessId, Long sampleId, Long testItemId);

    /** 发送给指定用户 */
    void sendToUser(Long userId, String type, String title, String content, Long businessId, Long sampleId, Long testItemId);

    /** 查询当前用户可见消息 (分页) */
    IPage<Message> pageForUser(Long userId, Long deptId, String type, int page, int size);

    /** 未读消息数 */
    long unreadCount(Long userId, Long deptId);

    /** 标记单条已读 (校验归属) */
    void markRead(Long id, Long userId, Long deptId);

    /** 全部标记已读 */
    void markAllRead(Long userId, Long deptId);

    /** 删除消息 (校验归属) */
    void deleteMessage(Long id, Long userId, Long deptId);

    /** 获取当前登录用户 ID (从安全上下文) */
    Long currentUserId();

    /** 获取当前登录用户所属科室 ID */
    Long currentDeptId();
}