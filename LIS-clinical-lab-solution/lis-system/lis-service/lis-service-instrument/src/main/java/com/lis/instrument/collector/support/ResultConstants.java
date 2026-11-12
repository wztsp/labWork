package com.lis.instrument.collector.support;

/**
 * 结果结构化字段常量
 * <p>
 * 与 {@code ResultMatchingService} 解析结果的 Map key 保持一致。
 */
public final class ResultConstants {

    private ResultConstants() {
    }

    /** 样本号/条码 */
    public static final String SAMPLE_NO = "sampleNo";
    /** 检验项目代码 */
    public static final String TEST_CODE = "testCode";
    /** 结果值 */
    public static final String VALUE = "value";
    /** 单位 */
    public static final String UNIT = "unit";
    /** 参考范围 */
    public static final String REF_RANGE = "refRange";
    /** 异常标志 (H/L/*** 等) */
    public static final String ABNORMAL_FLAG = "abnormalFlag";
    /** 序号 (部分协议无条码, 作为样本号兜底) */
    public static final String SEQ = "seq";
}