package com.lis.micro.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则中 细菌编码 / 抗生素编码 列表的工具: 兼容 JSON 数组("[A","B"]") 与逗号分隔("A,B")。
 */
public final class CodeListUtil {

    private CodeListUtil() {
    }

    /**
     * 解析编码列表。
     *
     * @param raw 原始串, 可为 null / 空 / JSON 数组 / 逗号分隔
     * @return 去空、去重后的编码列表; 异常时返回空列表
     */
    public static List<String> parseCodes(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return result;
        }
        // 剥离 [] 与引号, 兼容 JSON 数组
        s = s.replaceAll("[\\[\\]\"{}]", "");
        for (String part : s.split(",")) {
            String code = part.trim();
            if (!code.isEmpty() && !result.contains(code)) {
                result.add(code);
            }
        }
        return result;
    }

    /**
     * 判断目标编码是否命中给定编码列表 (空列表 = 适用于所有)。
     */
    public static boolean matchesAny(String targetCode, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return true;
        }
        if (targetCode == null) {
            return false;
        }
        for (String c : codes) {
            if (c.equalsIgnoreCase(targetCode.trim())) {
                return true;
            }
        }
        return false;
    }
}