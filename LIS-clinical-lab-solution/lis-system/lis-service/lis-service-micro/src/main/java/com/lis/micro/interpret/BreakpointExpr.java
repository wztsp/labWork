package com.lis.micro.interpret;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 折点 / 原始值表达式值对象。
 *
 * <p>支持形如 {@code ≤0.5}、{@code >=8}、{@code &lt;2}、{@code 18}、{@code =4} 的字符串，
 * 同时兼容全角字符（{@code ＜ ＞ ＝ ０-９ ．}）与空白。解析失败时返回
 * {@link #isValid()} 为 {@code false} 的实例，<b>绝不抛异常</b>。</p>
 *
 * <p>不可变对象，线程安全。数值一律使用 {@link BigDecimal} + {@code compareTo} 比较，
 * 不使用 {@code double} / {@code equals}。</p>
 */
public final class BreakpointExpr {

    /** 比较操作符 */
    public enum Op {
        /** 小于等于 ≤ / &lt;= */
        LE,
        /** 小于 &lt; */
        LT,
        /** 大于等于 ≥ / &gt;= */
        GE,
        /** 大于 &gt; */
        GT,
        /** 等于 = */
        EQ,
        /** 无操作符, 纯数值 */
        NONE
    }

    /** 纯数值匹配: 支持可选正负号、整数、小数 */
    private static final Pattern NUMERIC = Pattern.compile("^[+-]?(\\d+(\\.\\d+)?|\\.\\d+)$");

    private final Op op;
    private final BigDecimal value;
    private final String raw;
    private final boolean valid;

    private BreakpointExpr(Op op, BigDecimal value, String raw, boolean valid) {
        this.op = op;
        this.value = value;
        this.raw = raw;
        this.valid = valid;
    }

    /**
     * 解析表达式字符串。
     *
     * @param raw 原始串, 可为 null
     * @return 解析结果; 非法串返回 {@code isValid()==false} 的实例
     */
    public static BreakpointExpr parse(String raw) {
        if (raw == null) {
            return invalid(null);
        }
        String s = normalize(raw);
        if (s.isEmpty()) {
            return invalid(raw);
        }

        Op op = Op.NONE;
        String rest = s;
        if (s.startsWith("\u2264") || s.startsWith("\u2A7D")) {
            // ≤ / ⩽
            op = Op.LE;
            rest = s.substring(1);
        } else if (s.startsWith("\u2265") || s.startsWith("\u2A7E")) {
            // ≥ / ⩾
            op = Op.GE;
            rest = s.substring(1);
        } else if (s.startsWith("<=") || s.startsWith("=<")) {
            op = Op.LE;
            rest = s.substring(2);
        } else if (s.startsWith(">=") || s.startsWith("=>")) {
            op = Op.GE;
            rest = s.substring(2);
        } else if (s.startsWith("<")) {
            op = Op.LT;
            rest = s.substring(1);
        } else if (s.startsWith(">")) {
            op = Op.GT;
            rest = s.substring(1);
        } else if (s.startsWith("=")) {
            op = Op.EQ;
            rest = s.substring(1);
        }

        if (!NUMERIC.matcher(rest).matches()) {
            return invalid(raw);
        }
        try {
            return new BreakpointExpr(op, new BigDecimal(rest), raw, true);
        } catch (NumberFormatException e) {
            return invalid(raw);
        }
    }

    /** 构造一个非法（无法解析）的表达式 */
    public static BreakpointExpr invalid(String raw) {
        return new BreakpointExpr(Op.NONE, null, raw, false);
    }

    /**
     * 判断给定数值是否落在本表达式描述的区间内。
     *
     * @param v 待判定数值
     * @return 非法表达式或 v 为 null 时恒为 false
     */
    public boolean matches(BigDecimal v) {
        if (!valid || v == null || value == null) {
            return false;
        }
        int cmp = v.compareTo(value);
        switch (op) {
            case LE:
                return cmp <= 0;
            case LT:
                return cmp < 0;
            case GE:
                return cmp >= 0;
            case GT:
                return cmp > 0;
            case EQ:
            case NONE:
            default:
                return cmp == 0;
        }
    }

    /** 是否为「上界」型表达式 (≤ / &lt;)，即真实值不超过 value */
    public boolean isUpperBound() {
        return valid && (op == Op.LE || op == Op.LT);
    }

    /** 是否为「下界」型表达式 (≥ / &gt;)，即真实值不低于 value */
    public boolean isLowerBound() {
        return valid && (op == Op.GE || op == Op.GT);
    }

    public Op getOp() {
        return op;
    }

    /** 数值; 非法表达式返回 null */
    public BigDecimal getValue() {
        return value;
    }

    /** 原始输入串 */
    public String getRaw() {
        return raw;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * 归一化: 全角转半角, 去除所有空白字符。
     */
    private static String normalize(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '\uFF10' && c <= '\uFF19') {
                // 全角数字 ０-９
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\uFF0E') {
                sb.append('.');
            } else if (c == '\uFF1C') {
                sb.append('<');
            } else if (c == '\uFF1E') {
                sb.append('>');
            } else if (c == '\uFF1D') {
                sb.append('=');
            } else if (c == '\uFF0B') {
                sb.append('+');
            } else if (c == '\uFF0D') {
                sb.append('-');
            } else if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        if (!valid) {
            return "BreakpointExpr(invalid, raw=" + raw + ")";
        }
        return "BreakpointExpr(" + op + ", " + value.toPlainString() + ")";
    }
}
