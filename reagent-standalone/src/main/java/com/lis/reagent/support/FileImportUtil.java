package com.lis.reagent.support;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 试剂模块通用文件导入/模板工具。
 *
 * <p>支持 .xlsx/.xls（POI）与纯文本（逗号 CSV / 制表符）两类文件解析；
 * 解析结果统一为 {@code List<List<String>>} 行列结构（不包含表头行），便于各单据控制器复用。</p>
 */
public final class FileImportUtil {

    private FileImportUtil() {
    }

    /**
     * 根据文件后缀解析上传文件，返回数据行（不含表头），统一为 List<String> 列。
     */
    public static List<List<String>> parseRows(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return readExcelRows(file.getInputStream());
        }
        return readTextRows(new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    /**
     * 解析 .xlsx/.xls（POI），第一行为表头，跳过空行。
     */
    private static List<List<String>> readExcelRows(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            // 定位数据起始行：跳过头部的 sep= 行与真实表头行
            int dataStart = -1;
            boolean headerFound = false;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String first = row.getCell(0) == null ? "" : fmt.formatCellValue(row.getCell(0)).trim();
                if (first.isEmpty() && row.getLastCellNum() <= 1) {
                    continue;
                }
                if (!headerFound && first.equalsIgnoreCase("sep=,")) {
                    continue;
                }
                if (!headerFound) {
                    headerFound = true; // 真实表头行，跳过
                    continue;
                }
                dataStart = i;
                break;
            }
            if (dataStart < 0) {
                return rows;
            }
            for (int i = dataStart; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                int lastCell = Math.max(1, row.getLastCellNum());
                List<String> cells = new ArrayList<>(lastCell);
                boolean empty = true;
                for (int c = 0; c < lastCell; c++) {
                    Cell cell = row.getCell(c);
                    String val = cell == null ? "" : fmt.formatCellValue(cell).trim();
                    cells.add(val);
                    if (!val.isEmpty()) {
                        empty = false;
                    }
                }
                if (!empty) {
                    rows.add(cells);
                }
            }
        }
        return rows;
    }

    /**
     * 解析文本内容（逗号 CSV 或制表符），第一行为表头，跳过空行与 sep= 声明行。
     */
    private static List<List<String>> readTextRows(String content) {
        List<List<String>> rows = new ArrayList<>();
        String[] lines = content.replace("\uFEFF", "").split("\\r?\\n");
        // 定位真正的表头：跳过空行与 sep= 指令行，表头后的行均为数据
        int headerIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            if (line.trim().toLowerCase().startsWith("sep=")) {
                continue;
            }
            headerIdx = i;
            break;
        }
        if (headerIdx < 0) {
            return rows;
        }
        for (int i = headerIdx + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            rows.add(splitImportLine(line));
        }
        return rows;
    }

    /**
     * 按行解析：含逗号/引号按 CSV 规则解析，否则兼容制表符分隔。
     */
    private static List<String> splitImportLine(String line) {
        if (line.indexOf(',') >= 0 || line.indexOf('"') >= 0) {
            return parseCsvLine(line);
        }
        if (line.indexOf('\t') >= 0) {
            return new ArrayList<>(Arrays.asList(line.split("\t", -1)));
        }
        return new ArrayList<>(Collections.singletonList(line));
    }

    /**
     * 解析单行 CSV（支持双引号包裹、双引号转义）。
     */
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuote = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    /**
     * 取列值：越界或空白返回 null。
     */
    public static String cell(List<String> cols, int idx) {
        if (idx >= cols.size()) return null;
        String s = cols.get(idx);
        if (s == null || s.trim().isEmpty()) return null;
        return s.trim();
    }

    /**
     * CSV 字段转义（含分隔符/引号/换行时用双引号包裹）。
     */
    public static String csvField(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (needQuote) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * 输出 UTF-8 CSV 模板到响应（带 BOM，含表头与可选示例行），扩展名 .csv。
     *
     * @param header 表头列（逗号分隔的文案，如 "试剂编码,数量"）
     * @param rows   示例/数据行，可为空
     */
    public static void writeCsvTemplate(HttpServletResponse response, String filename, String header, List<List<String>> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        // sep=, 声明分隔符为逗号，强制 Excel 按列拆分（避免非英文区域打开成单列）
        sb.append("sep=,\r\n");
        sb.append(header).append("\r\n");
        for (List<String> row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) line.append(',');
                line.append(csvField(row.get(i)));
            }
            sb.append(line).append("\r\n");
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = response.getOutputStream()) {
            out.write(bytes);
            out.flush();
        }
    }
}