package com.lis.report.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量操作统一返回结果
 */
@Data
public class BatchOpResult {
    /** 提交标本数 */
    private int total;
    /** 成功数 */
    private int success;
    /** 失败数 */
    private int failed;
    /** 已跳过数 */
    private int skipped;
    /** 失败 / 跳过的详细说明 */
    private List<String> messages;

    public static BatchOpResult of(int total, int success) {
        BatchOpResult r = new BatchOpResult();
        r.total = total;
        r.success = success;
        r.failed = total - success;
        r.skipped = 0;
        r.messages = new ArrayList<>();
        return r;
    }

    public static BatchOpResult of(int total, int success, List<String> messages) {
        BatchOpResult r = of(total, success);
        r.messages = messages != null ? messages : new ArrayList<>();
        return r;
    }

    public void addMessage(String msg) {
        if (messages == null) messages = new ArrayList<>();
        messages.add(msg);
    }
}
