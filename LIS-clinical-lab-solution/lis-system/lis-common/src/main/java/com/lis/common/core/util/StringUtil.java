package com.lis.common.core.util;

import java.util.UUID;

/**
 * 字符串工具类
 */
public class StringUtil {

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 生成 UUID (无横线)
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成业务编号: 前缀 + yyyyMMddHHmmss + 4位随机数
     */
    public static String genBizNo(String prefix) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 9000) + 1000;
        return prefix + timestamp + random;
    }

    /**
     * 脱敏处理 - 手机号: 138****8000
     */
    public static String maskPhone(String phone) {
        if (isEmpty(phone) || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 脱敏处理 - 身份证: 110101****1234
     */
    public static String maskIdCard(String idCard) {
        if (isEmpty(idCard) || idCard.length() < 8) return idCard;
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 脱敏处理 - 姓名: 张**
     */
    public static String maskName(String name) {
        if (isEmpty(name)) return name;
        if (name.length() == 1) return name;
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}
