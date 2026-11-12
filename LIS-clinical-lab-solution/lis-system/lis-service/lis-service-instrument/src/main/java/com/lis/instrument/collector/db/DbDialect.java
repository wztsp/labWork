package com.lis.instrument.collector.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC 方言: 根据数据库类型构建驱动与连接 URL
 */
public final class DbDialect {

    private DbDialect() {
    }

    /**
     * 根据 dbType 推断驱动类名; 允许通过 dbDriver 显式覆盖
     */
    public static String resolveDriver(String dbType, String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        if (dbType == null) {
            return null;
        }
        return switch (dbType.trim().toUpperCase()) {
            case "POSTGRESQL", "POSTGRES" -> "org.postgresql.Driver";
            case "MYSQL" -> "com.mysql.cj.jdbc.Driver";
            case "SQLSERVER", "MSSQL" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "ORACLE" -> "oracle.jdbc.OracleDriver";
            default -> null;
        };
    }

    /**
     * 构建 JDBC URL
     */
    public static String buildUrl(String dbType, String host, Integer port, String dbName) {
        String type = dbType == null ? "" : dbType.trim().toUpperCase();
        String h = host == null ? "localhost" : host;
        int p = port == null ? defaultPort(type) : port;
        return switch (type) {
            case "POSTGRESQL", "POSTGRES" ->
                    "jdbc:postgresql://" + h + ":" + p + "/" + safeDb(dbName);
            case "MYSQL" ->
                    "jdbc:mysql://" + h + ":" + p + "/" + safeDb(dbName)
                            + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true";
            case "SQLSERVER", "MSSQL" ->
                    "jdbc:sqlserver://" + h + ":" + p + ";databaseName=" + safeDb(dbName)
                            + ";encrypt=false;trustServerCertificate=true";
            case "ORACLE" ->
                    "jdbc:oracle:thin:@" + h + ":" + p + ":" + safeDb(dbName);
            default -> throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        };
    }

    private static int defaultPort(String type) {
        return switch (type) {
            case "POSTGRESQL", "POSTGRES" -> 5432;
            case "MYSQL" -> 3306;
            case "SQLSERVER", "MSSQL" -> 1433;
            case "ORACLE" -> 1521;
            default -> 0;
        };
    }

    private static String safeDb(String dbName) {
        return dbName == null ? "" : dbName.trim();
    }

    /**
     * 解析列映射: "field=sourceCol,field2=sourceCol2,..." -> 有序 LinkedHashMap
     */
    public static LinkedHashMap<String, String> parseColumnMapping(String dbColumns) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if (dbColumns == null || dbColumns.isBlank()) {
            return map;
        }
        for (String part : dbColumns.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq > 0) {
                map.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
            } else {
                // 未指定等号, 视为字段名与源列同名
                map.put(p, p);
            }
        }
        return map;
    }

    /**
     * 读取结果集为 List<Map<String,Object>> (value 保留原始类型)
     */
    public static List<Map<String, Object>> readRows(ResultSet rs) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        Map<String, Object> row;
        while (rs.next()) {
            row = new HashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 打开连接 (加载驱动失败时抛出明确异常)
     */
    public static Connection open(String url, String user, String password, String driver)
            throws Exception {
        if (driver != null && !driver.isBlank()) {
            Class.forName(driver);
        }
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * 从对象宽松转字符串 (时间戳/日期转 ISO 字符串)
     */
    public static String toText(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (val instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        return String.valueOf(val);
    }
}