package com.lis.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局配置:
 * 1. Long 序列化为 String (防止雪花ID精度丢失)
 * 2. LocalDateTime / LocalDate 支持多种格式:
 *    - "yyyy-MM-dd HH:mm:ss"
 *    - "yyyy-MM-dd'T'HH:mm:ss"
 *    - "yyyy-MM-dd"
 * 3. 序列化时统一输出 "yyyy-MM-dd HH:mm:ss" 格式
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            // Long -> String 序列化 (仅引用类型, 保证雪花ID精度不丢失)
            builder.serializerByType(Long.class, new LongToStringSerializer());
            // 注意: 不再序列化基本类型 long。若注册 Long.TYPE 会把 PageResult.total/current/size
            // 以及各类统计计数字段都变成字符串, 导致前端 el-pagination 的 :total 收到 String 而告警。
            // 实体主键一律为引用类型 Long, 不受影响。

            // JavaTimeModule: 自定义 LocalDateTime / LocalDate 格式
            builder.modules(new JavaTimeModule());
            builder.serializerByType(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
            builder.serializerByType(LocalDate.class,
                    new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));

            // 反序列化: 支持多种格式
            builder.deserializerByType(LocalDateTime.class,
                    new MultiFormatLocalDateTimeDeserializer());
            builder.deserializerByType(LocalDate.class,
                    new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));

            // 关闭时间戳序列化
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // 允许未知字段
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }

    /**
     * Long -> String 序列化器
     */
    public static class LongToStringSerializer extends StdSerializer<Long> {
        public LongToStringSerializer() {
            super(Long.class);
        }

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value != null) {
                gen.writeString(value.toString());
            }
        }
    }

    /**
     * 支持多种格式的 LocalDateTime 反序列化器
     * 1. "yyyy-MM-dd HH:mm:ss"
     * 2. "yyyy-MM-dd'T'HH:mm:ss"
     * 3. "yyyy-MM-dd'T'HH:mm:ss.SSS"
     * 4. ISO 标准格式
     */
    public static class MultiFormatLocalDateTimeDeserializer extends FromStringDeserializer<LocalDateTime> {
        private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        private static final DateTimeFormatter ISO_WITH_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

        public MultiFormatLocalDateTimeDeserializer() {
            super(LocalDateTime.class);
        }

        @Override
        protected LocalDateTime _deserialize(String value, DeserializationContext ctxt) throws IOException {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            String trimmed = value.trim();
            try {
                // 1. 优先尝试 "yyyy-MM-dd HH:mm:ss"
                return LocalDateTime.parse(trimmed, DEFAULT_FORMATTER);
            } catch (Exception e1) {
                try {
                    // 2. 尝试 ISO 标准格式 (含 T)
                    return LocalDateTime.parse(trimmed, ISO_FORMATTER);
                } catch (Exception e2) {
                    try {
                        // 3. 尝试 ISO 格式含毫秒
                        return LocalDateTime.parse(trimmed, ISO_WITH_MILLIS);
                    } catch (Exception e3) {
                        // 4. 尝试仅日期
                        try {
                            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern(DATE_PATTERN)).atStartOfDay();
                        } catch (Exception e4) {
                            throw new IOException("无法解析日期时间: " + value
                                    + "，支持格式: yyyy-MM-dd HH:mm:ss / yyyy-MM-dd'T'HH:mm:ss", e4);
                        }
                    }
                }
            }
        }
    }
}
