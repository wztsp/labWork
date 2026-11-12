package com.lis.common.core.constant;

/**
 * 正则常量
 */
public class RegexConstant {

    /** 手机号 */
    public static final String PHONE = "^1[3-9]\\d{9}$";
    /** 身份证号 */
    public static final String ID_CARD = "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$";
    /** 邮箱 */
    public static final String EMAIL = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    /** 标本条码 */
    public static final String SAMPLE_BARCODE = "^[A-Za-z0-9\\-]{1,32}$";
    /** 日期时间 */
    public static final String DATETIME = "^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}$";
}
