package com.lis.common.core.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期工具类
 */
public class DateUtil {

    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_TIME = "HH:mm:ss";

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ofPattern(PATTERN_DATETIME)) : null;
    }

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ofPattern(PATTERN_DATE)) : null;
    }

    public static LocalDateTime parseDateTime(String str) {
        return str != null ? LocalDateTime.parse(str, DateTimeFormatter.ofPattern(PATTERN_DATETIME)) : null;
    }

    public static LocalDate parseDate(String str) {
        return str != null ? LocalDate.parse(str, DateTimeFormatter.ofPattern(PATTERN_DATE)) : null;
    }

    public static Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDateTime toDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static String nowStr() {
        return formatDateTime(LocalDateTime.now());
    }

    /**
     * 计算年龄(岁)
     */
    public static int calcAge(LocalDate birthDate) {
        if (birthDate == null) return 0;
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
