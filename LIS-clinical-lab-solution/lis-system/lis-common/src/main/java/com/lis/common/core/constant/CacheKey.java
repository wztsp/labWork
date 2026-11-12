package com.lis.common.core.constant;

/**
 * 缓存 Key 常量
 */
public class CacheKey {

    /** 用户会话 */
    public static final String USER_SESSION = "lis:user:session:";
    /** 用户权限 */
    public static final String USER_PERMISSIONS = "lis:user:permissions:";
    /** 账号撤销标记 (B-4 离职回收; 配合 Tier-2 网关按 iat 拒绝旧 token) */
    public static final String USER_REVOKED = "lis:user:revoked:";
    /** 标本状态 */
    public static final String SAMPLE_STATUS = "lis:sample:status:";
    /** 项目字典 */
    public static final String TEST_ITEM_DICT = "lis:dict:test_item";
    /** 组合字典 */
    public static final String PANEL_DICT = "lis:dict:panel";
    /** 参考范围 */
    public static final String REF_RANGE = "lis:dict:ref_range:";
    /** 危急值规则 */
    public static final String CRITICAL_RULE = "lis:rule:critical";
    /** 仪器在线状态 */
    public static final String INSTRUMENT_STATUS = "lis:instrument:status:";
    /** 分布式锁 */
    public static final String LOCK = "lis:lock:";

    /** Token 黑名单 */
    public static final String TOKEN_BLACKLIST = "lis:token:blacklist:";

    /** 默认过期时间(秒) */
    public static final long DEFAULT_TTL = 3600;
    /** 字典缓存过期时间 */
    public static final long DICT_TTL = 7200;
}
