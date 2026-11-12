package com.lis.monolith.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lis.common.core.result.R;
import com.lis.monolith.entity.Message;
import com.lis.monolith.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 消息提醒
 */
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 当前用户可见消息 (分页) */
    @GetMapping("/page")
    public R<IPage<Message>> page(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String type) {
        Long userId = messageService.currentUserId();
        Long deptId = messageService.currentDeptId();
        return R.ok(messageService.pageForUser(userId, deptId, type, page, size));
    }

    /** 当前用户未读消息数 */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(messageService.unreadCount(messageService.currentUserId(), messageService.currentDeptId()));
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id, messageService.currentUserId(), messageService.currentDeptId());
        return R.ok();
    }

    /** 全部标记已读 */
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        messageService.markAllRead(messageService.currentUserId(), messageService.currentDeptId());
        return R.ok();
    }

    /** 删除消息 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        messageService.deleteMessage(id, messageService.currentUserId(), messageService.currentDeptId());
        return R.ok();
    }
}