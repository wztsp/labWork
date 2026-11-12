package com.lis.common.notify;

import java.util.List;

/**
 * 通知服务接口 (微信/钉钉/邮件等)
 */
public interface NotifyService {

    /**
     * 发送告警通知
     * @param title 标题
     * @param content 内容
     * @param recipients 接收人列表 (手机号/用户ID/邮箱，依实现而定)
     */
    void sendAlert(String title, String content, List<String> recipients);
}
