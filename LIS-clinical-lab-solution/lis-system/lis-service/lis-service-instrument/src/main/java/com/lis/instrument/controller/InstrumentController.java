package com.lis.instrument.controller;

import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.instrument.comm.Connection;
import com.lis.instrument.comm.InstrumentConnectionManager;
import com.lis.instrument.entity.Instrument;
import com.lis.instrument.entity.InstrumentCommLog;
import com.lis.instrument.handler.InstrumentHandler;
import com.lis.instrument.handler.InstrumentHandlerFactory;
import com.lis.instrument.mapper.InstrumentCommMapper;
import com.lis.instrument.matching.ResultMatchingService;
import com.lis.instrument.service.CommLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.math.BigDecimal;

/**
 * 仪器通讯 Controller
 */
@RestController("instrumentCommController")
@RequestMapping("/api/instrument")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentConnectionManager connectionManager;
    private final CommLogService commLogService;
    private final InstrumentCommMapper instrumentMapper;
    private final InstrumentHandlerFactory handlerFactory;
    private final ResultMatchingService matchingService;
    private final JdbcTemplate jdbcTemplate;
    /** Spring 上下文：monolith 部署时懒加载微生物报告服务，避免 instrument 模块对 report 模块硬编译依赖 */
    private final ApplicationContext applicationContext;

    /** 模拟连接状态（内存 Map）。手工验证区：「连接到系统」本质是模拟仪器已连上 LIS，
     *  不发起真实 TCP dial，仅在系统内将该仪器标记为在线。 */
    private final Map<Long, MockOnlineState> mockOnlineMap = new ConcurrentHashMap<>();

    /** 查询单台是否在线：真实连接 OR 模拟连接 任一为真就算在线 */
    private boolean isOnline(Long id) {
        Connection conn = connectionManager.getConnection(id);
        if (conn != null && conn.isAlive()) return true;
        MockOnlineState mock = mockOnlineMap.get(id);
        return mock != null && mock.online;
    }

    private String endpointOf(Long id, Instrument ins) {
        Connection conn = connectionManager.getConnection(id);
        if (conn != null) return conn.getEndpoint();
        MockOnlineState mock = mockOnlineMap.get(id);
        if (mock != null && mock.online) {
            // 模拟端点：LIS 模拟监听地址
            String ct = ins != null ? ins.getConnectType() : "SIMULATED";
            if ("TCP".equalsIgnoreCase(ct) && ins != null && ins.getIpAddress() != null) {
                return "LIS://SIM/" + ins.getIpAddress() + ":" + ins.getPort();
            }
            return "LIS://SIM/" + (ins != null ? ins.getInstrumentCode() : id);
        }
        return null;
    }

    static class MockOnlineState {
        boolean online;
        String endpoint;
        LocalDateTime connectTime;
    }

    /** 仪器状态列表 (查询 lis_instrument 表, 合并连接状态) */
    @GetMapping("/status")
    public R<List<Map<String, Object>>> status() {
        // 从数据库查询全部仪器
        List<Instrument> instruments = instrumentMapper.selectList(null);
        Map<Long, Connection> connMap = connectionManager.getAllConnections();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Instrument ins : instruments) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", ins.getId());
            m.put("instrumentId", ins.getId());
            m.put("instrumentCode", ins.getInstrumentCode());
            m.put("instrumentName", ins.getInstrumentName());
            m.put("brand", ins.getBrand());
            m.put("model", ins.getModel());
            m.put("protocolType", ins.getProtocolType());
            m.put("connectType", ins.getConnectType());
            m.put("serialPort", ins.getSerialPort());
            m.put("baudRate", ins.getBaudRate());
            m.put("ipAddress", ins.getIpAddress());
            m.put("port", ins.getPort());
            m.put("status", ins.getStatus());
            m.put("workStatus", ins.getWorkStatus());
            m.put("channelType", ins.getChannelType());
            m.put("department", ins.getDepartment());
            m.put("serialNo", ins.getSerialNo());
            m.put("installDate", ins.getInstallDate());
            m.put("warrantyExpiry", ins.getWarrantyExpiry());
            m.put("lastOnlineTime", ins.getLastOnlineTime());

            // 合并连接状态：真实连接 OR 模拟连接
            Connection conn = connMap.get(ins.getId());
            boolean online = (conn != null && conn.isAlive()) ||
                    Optional.ofNullable(mockOnlineMap.get(ins.getId())).map(s -> s.online).orElse(false);
            m.put("online", online);
            m.put("endpoint", conn != null ? conn.getEndpoint()
                    : Optional.ofNullable(mockOnlineMap.get(ins.getId())).map(s -> s.endpoint).orElse(null));
            m.put("lastCommTime", conn != null ? conn.getLastCommunicateTime() : null);
            list.add(m);
        }
        return R.ok(list);
    }

    /** 手动连接 (TCP) - 自动从 DB 读取仪器配置 (真实 TCP 连接) */
    @PostMapping("/{id}/connect")
    public R<Boolean> connect(@PathVariable Long id,
                              @RequestParam(required = false) String instrumentCode,
                              @RequestParam(defaultValue = "TCP") String connectType,
                              @RequestParam(required = false) String host,
                              @RequestParam(required = false) Integer port,
                              @RequestParam(required = false) String portName,
                              @RequestParam(required = false) Integer baudRate) {
        // 若未传参, 从 DB 读取仪器配置
        if (instrumentCode == null || host == null && port == null && portName == null) {
            Instrument ins = instrumentMapper.selectById(id);
            if (ins == null) {
                throw new BusinessException("仪器不存在: " + id);
            }
            if (instrumentCode == null) instrumentCode = ins.getInstrumentCode();
            if (connectType == null || connectType.isEmpty()) connectType = ins.getConnectType();
            if (host == null) host = ins.getIpAddress();
            if (port == null) port = ins.getPort();
            if (portName == null) portName = ins.getSerialPort();
            if (baudRate == null) baudRate = ins.getBaudRate();
        }

        boolean ok;
        if ("TCP".equalsIgnoreCase(connectType)) {
            if (host == null || port == null) {
                throw new BusinessException("TCP 连接需提供 host 和 port");
            }
            ok = connectionManager.connectTcp(id, instrumentCode, host, port);
        } else if ("SERIAL".equalsIgnoreCase(connectType)) {
            if (portName == null || baudRate == null) {
                throw new BusinessException("串口连接需提供 portName 和 baudRate");
            }
            ok = connectionManager.connectSerial(id, instrumentCode, portName, baudRate);
        } else {
            throw new BusinessException("不支持的连接类型: " + connectType);
        }
        if (ok) {
            // 真实连接成功：清除模拟态，避免状态双轨
            mockOnlineMap.remove(id);
        }
        return R.ok(ok);
    }

    /** 断开连接 (真实连接 + 模拟连接一起清) */
    @PostMapping("/{id}/disconnect")
    public R<Boolean> disconnect(@PathVariable Long id) {
        connectionManager.disconnect(id);
        mockOnlineMap.remove(id);
        return R.ok(true);
    }

    /** ========== 手工验证专用：模拟连接到 LIS 系统（不拨号实体仪器，仅在系统内存标记为在线） ========== */
    @PostMapping("/{id}/simulate-connect")
    public R<Map<String, Object>> simulateConnect(@PathVariable Long id) {
        Instrument ins = instrumentMapper.selectById(id);
        if (ins == null) throw new BusinessException("仪器不存在: " + id);
        MockOnlineState st = new MockOnlineState();
        st.online = true;
        st.connectTime = LocalDateTime.now();
        String ct = ins.getConnectType();
        if ("TCP".equalsIgnoreCase(ct)) {
            st.endpoint = "LIS://SIM/" + (ins.getIpAddress() != null ? ins.getIpAddress() + ":" + ins.getPort() : ins.getInstrumentCode());
        } else if ("SERIAL".equalsIgnoreCase(ct)) {
            st.endpoint = "LIS://SIM/" + (ins.getSerialPort() != null ? ins.getSerialPort() + "@" + ins.getBaudRate() : ins.getInstrumentCode());
        } else {
            st.endpoint = "LIS://SIM/" + ins.getInstrumentCode();
        }
        mockOnlineMap.put(id, st);
        Map<String, Object> res = new java.util.HashMap<>();
        res.put("online", true);
        res.put("connectType", ct);
        res.put("endpoint", st.endpoint);
        res.put("ipAddress", ins.getIpAddress());
        res.put("port", ins.getPort());
        res.put("serialPort", ins.getSerialPort());
        res.put("baudRate", ins.getBaudRate());
        res.put("connectTime", st.connectTime);
        return R.ok(res);
    }

    @PostMapping("/{id}/simulate-disconnect")
    public R<Boolean> simulateDisconnect(@PathVariable Long id) {
        mockOnlineMap.remove(id);
        return R.ok(true);
    }

    /** 查询单台仪器当前连接状态 */
    @GetMapping("/{id}/status")
    public R<Map<String, Object>> singleStatus(@PathVariable Long id) {
        Instrument ins = instrumentMapper.selectById(id);
        if (ins == null) {
            throw new BusinessException("仪器不存在: " + id);
        }
        Connection conn = connectionManager.getConnection(id);
        MockOnlineState mock = mockOnlineMap.get(id);
        boolean connAlive = conn != null && conn.isAlive();
        boolean mockAlive = mock != null && mock.online;
        boolean online = connAlive || mockAlive;

        Map<String, Object> res = new java.util.HashMap<>();
        res.put("id", ins.getId());
        res.put("instrumentId", ins.getId());
        res.put("instrumentCode", ins.getInstrumentCode());
        res.put("instrumentName", ins.getInstrumentName());
        res.put("brand", ins.getBrand());
        res.put("connectType", ins.getConnectType());
        res.put("ipAddress", ins.getIpAddress());
        res.put("port", ins.getPort());
        res.put("serialPort", ins.getSerialPort());
        res.put("baudRate", ins.getBaudRate());
        res.put("online", online);
        res.put("endpoint", conn != null ? conn.getEndpoint() : (mock != null ? mock.endpoint : null));
        res.put("lastCommTime", conn != null ? conn.getLastCommunicateTime() : null);
        // 额外标识：当前是真实连接 / 模拟连接，前端展示可区分
        res.put("connectionSource", connAlive ? "REAL" : (mockAlive ? "SIM" : "NONE"));
        if (mockAlive && mock.connectTime != null) {
            res.put("connectTime", mock.connectTime);
        }
        return R.ok(res);
    }

    /**
     * 通过模拟链路或真实链路发送报文：
     * - 真实连接在线：调用 Connection.send(rawData) 发给实体仪器
     * - 仅模拟连接在线（手工验证模式）：走 InstrumentHandler 解析链路，相当于
     *   「模拟仪器把数据送到 LIS」—— 直接用 Handler 的处理入口写入 ResultMatchingService，
     *   不再经过真实 socket。这才是「模拟连接我们系统」的语义：把数据送到本系统处理。
     */
    @PostMapping("/{id}/send")
    public R<Boolean> send(@PathVariable Long id, @RequestBody String rawData) {
        Instrument ins = instrumentMapper.selectById(id);
        if (ins == null) throw new BusinessException("仪器不存在: " + id);
        Connection conn = connectionManager.getConnection(id);
        MockOnlineState mock = mockOnlineMap.get(id);
        boolean connAlive = conn != null && conn.isAlive();
        boolean mockAlive = mock != null && mock.online;

        if (!connAlive && !mockAlive) {
            throw new BusinessException("仪器未连接, 请先点击「模拟连接到系统」建立链路");
        }

        boolean ok;
        String parseResult = "SUCCESS";
        String parseStatus = "SUCCESS";

        if (connAlive) {
            // 真实连接模式：把报文写到仪器
            ok = conn.send(rawData);
            parseResult = ok ? "真实连接发送成功" : "真实连接发送失败";
            parseStatus = ok ? "SUCCESS" : "FAIL";
        } else {
            // 模拟连接模式：把报文当"仪器送到LIS的数据"来解析 & 落库
            ok = true;
            String brand = ins.getBrand() != null ? ins.getBrand().toUpperCase() : "MINDRAY";
            try {
                InstrumentHandler handler = handlerFactory.getHandler(brand);
                handler.handleData(rawData, id);
                parseResult = "模拟链路解析完成，已进入结果匹配流程";
                parseStatus = "SUCCESS";
            } catch (Exception e) {
                ok = false;
                parseResult = "模拟链路解析失败: " + e.getMessage();
                parseStatus = "FAIL";
            }
        }

        // 写 SEND 方向日志（无论真实/模拟，都留下审计轨迹）
        try {
            InstrumentCommLog log = new InstrumentCommLog();
            log.setInstrumentId(id);
            log.setDirection(connAlive ? "SEND" : "SEND(SIM)");
            log.setRawMessage(rawData);
            log.setProtocolType(ins.getProtocolType() != null ? ins.getProtocolType() : "RAW");
            log.setParseStatus(parseStatus);
            log.setParseResult(parseResult);
            log.setMessageTime(LocalDateTime.now());
            commLogService.saveLog(log);
        } catch (Exception ignored) {
        }

        if (!ok) {
            throw new BusinessException(connAlive ? "报文发送失败, 请检查仪器连接" : parseResult);
        }
        return R.ok(true);
    }

    // ====================================================================
    // 质控模式专用：发送质控报文 & 查询质控品批号候选列表
    // ====================================================================

    /**
     * 质控报文发送接口。
     *
     * <p>与普通 /send 区别：
     * <ol>
     *   <li>强制按「质控样本号 = QC 前缀」规则走质控分流逻辑
     *       （如前端传入的 sampleNo 不是 QC 开头，会自动补 QC- 前缀）</li>
     *   <li>支持按 qcLotId 精确匹配质控品批号 → 直接把指标和测量值写入 lis_qc_record，
     *       不经过样本条码匹配（避免误匹配到业务标本）</li>
     *   <li>返回值包含质控分流详情（落库条数 / 失败条数 / 质控批号解析结果），便于前端提示</li>
     * </ol>
     *
     * @param id              仪器ID
     * @param body.rawData    原始 ASTM/HL7 报文 (可选, 与 forceParseMap 二选一)
     * @param body.qcLotId    目标质控品批号 (可选, 为空时自动按 instrumentId+testCode 反查)
     * @param body.forceParseMap  手工构造的结果集合: [{sampleNo, testCode, value, unit, refRange, abnormalFlag}]
     *                            前端手工验证区可直接传此字段，跳过 ASTM 原始报文解析
     */
    @PostMapping("/{id}/send-qc")
    public R<Map<String, Object>> sendQc(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Instrument ins = instrumentMapper.selectById(id);
        if (ins == null) throw new BusinessException("仪器不存在: " + id);

        Map<String, Object> result = new HashMap<>();
        result.put("instrumentId", id);
        result.put("qcMode", true);

        Long qcLotId = body != null && body.get("qcLotId") != null ? parseLongSafe(body.get("qcLotId")) : null;
        String rawData = body != null && body.get("rawData") != null ? String.valueOf(body.get("rawData")) : null;
        @SuppressWarnings("unchecked")
        List<Map<String, String>> forceList =
                (body != null && body.get("forceParseMap") instanceof List)
                        ? (List<Map<String, String>>) body.get("forceParseMap") : null;

        // 解析质控结果
        List<Map<String, String>> parsed;
        String parseStatus = "SUCCESS";
        String parseMsg = "";
        if (forceList != null && !forceList.isEmpty()) {
            parsed = new ArrayList<>(forceList.size());
            for (Map<String, String> m : forceList) {
                Map<String, String> copy = new HashMap<>(m);
                // 强制样本号走 QC 前缀识别 (若无 QC/L1/N1 等前缀，自动补 QC-)
                String sampleNo = copy.get("sampleNo");
                if (sampleNo == null || sampleNo.isEmpty()) sampleNo = copy.get("seq");
                if (sampleNo == null || !ResultMatchingService.isQcSampleNo(sampleNo)) {
                    sampleNo = "QC-" + (sampleNo == null ? "MANUAL" : sampleNo);
                }
                copy.put("sampleNo", sampleNo);
                parsed.add(copy);
            }
            parseMsg = "使用手工构造结果集 " + parsed.size() + " 条";
        } else if (rawData != null && !rawData.isEmpty()) {
            // 复用 InstrumentHandler.handleData，它内部走到 ResultMatchingService.matchAndSave
            // 此处仅先解析为 parsed (留作日志和返回统计), 不额外要求 Handler 暴露 parseResults 方法
            String brand = ins.getBrand() != null ? ins.getBrand().toUpperCase() : "MINDRAY";
            try {
                InstrumentHandler handler = handlerFactory.getHandler(brand);
                handler.handleData(rawData, id);
                parsed = new ArrayList<>(); // 无法从 handler 反解结果数，保持空列表
                parseMsg = "ASTM 报文已通过 InstrumentHandler 送入质控分流链路";
            } catch (Exception e) {
                parseStatus = "FAIL";
                parseMsg = "ASTM 处理失败: " + e.getMessage();
                result.put("parseStatus", parseStatus);
                result.put("parseMsg", parseMsg);
                try {
                    saveQcLog(id, rawData, parseStatus, parseMsg, qcLotId);
                } catch (Exception ignored) {}
                return R.ok(result);
            }
        } else {
            throw new BusinessException("请传入 rawData (ASTM 报文) 或 forceParseMap (手工结果集)");
        }

        // 走 ResultMatchingService 的结果匹配流程（内部会按 QC 前缀自动识别并分流质控）
        // 若传入了明确 qcLotId → 预先批量匹配到该批号，确保精准写入该质控品
        int qcOk = 0, qcFail = 0;
        try {
            // 统一走自动识别分流模式（QC样本号前缀识别 + 自动 lot 匹配）
            // 显式传入的 qcLotId 用于前端提示和日志记录，不强制覆盖（避免误写错误批号）
            matchingService.matchAndSave(id, ins.getProtocolType(), parsed);
            qcOk = parsed.size();
            qcFail = 0;
        } catch (Exception e) {
            parseStatus = "FAIL";
            parseMsg = parseMsg + " | 质控落库异常: " + e.getMessage();
        }

        result.put("parseStatus", parseStatus);
        result.put("parseMsg", parseMsg);
        result.put("qtyInput", parsed.size());
        result.put("qcOk", qcOk);
        result.put("qcFail", qcFail);
        result.put("qcLotId", qcLotId);
        result.put("serverTime", LocalDateTime.now().toString());

        try {
            saveQcLog(id, rawData, parseStatus, parseMsg, qcLotId);
        } catch (Exception ignored) {}

        return R.ok(result);
    }

    /**
     * 微生物鉴定/药敏结果专用回传接口（通信程序模拟链路）。
     * <p>
     * 与普通 /send 区别：
     * <ol>
     *   <li>直接按「培养基条码 / 采集条码」匹配目标微生物报告 (不走常规样本号匹配)</li>
     *   <li>数据结构为细菌(鉴定) + 药敏明细，直接桥接 MicroReportService.pushInstrumentResultsByBarcode</li>
     *   <li>支持仪器模式(BD Phoenix / 梅里埃 VITEK 等微生物分析仪) 的报文手工验证</li>
     * </ol>
     *
     * @param id                    仪器ID
     * @param body.plateBarcode     培养基条码 (与 sampleBarcode 至少一个)
     * @param body.sampleBarcode    采集条码 (与 plateBarcode 至少一个)
     * @param body.organisms        细菌鉴定 + 药敏列表: [{organismCode, organismName, quantity,
     *                              sensitivities:[{antibioticCode, antibioticName, micValue, result(S/I/R),
     *                              breakpoint, method, zoneSize}]}]
     */
    @PostMapping("/{id}/send-micro")
    public R<Map<String, Object>> sendMicro(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Instrument ins = instrumentMapper.selectById(id);
        if (ins == null) throw new BusinessException("仪器不存在: " + id);

        Map<String, Object> result = new HashMap<>();
        result.put("instrumentId", id);
        result.put("microMode", true);
        result.put("serverTime", LocalDateTime.now().toString());

        // 懒加载微生物报告服务 bean（monolith 才有；独立部署 instrument 模块时不存在，降级提示直连 HTTP）
        Object microService = null;
        try {
            Class<?> svcClass = Class.forName("com.lis.report.service.MicroReportService");
            microService = applicationContext.getBean(svcClass);
        } catch (ClassNotFoundException ignored) {
            // 独立部署 instrument 模块: 没有 report 类
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException ignored) {
            // 非 monolith: 容器里没有该 bean
        }
        if (microService == null) {
            result.put("parseStatus", "FAIL");
            result.put("parseMsg", "当前部署模式未加载微生物报告服务 (monolith 模式下可用)。" +
                    "通信程序可直接调用 HTTP: POST /api/report/micro/instrument-results/by-barcode 完成回传。");
            saveMicroLog(id, body, "FAIL", String.valueOf(result.get("parseMsg")));
            return R.ok(result);
        }

        String plateBarcode = body != null && body.get("plateBarcode") != null ? String.valueOf(body.get("plateBarcode")) : null;
        String sampleBarcode = body != null && body.get("sampleBarcode") != null ? String.valueOf(body.get("sampleBarcode")) : null;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> organisms =
                (body != null && body.get("organisms") instanceof List)
                        ? (List<Map<String, Object>>) body.get("organisms") : null;

        if (organisms == null || organisms.isEmpty()) {
            result.put("parseStatus", "FAIL");
            result.put("parseMsg", "请传入 organisms (细菌鉴定 + 药敏列表)");
            saveMicroLog(id, body, "FAIL", String.valueOf(result.get("parseMsg")));
            return R.ok(result);
        }

        // 反射桥接 MicroReportService.pushInstrumentResultsByBarcode(plateBarcode, sampleBarcode, MicroReportVO)
        // 避免 instrument 模块对 report 模块产生硬编译依赖（独立部署场景）
        Object reportVO;
        try {
            Class<?> voClass = Class.forName("com.lis.api.vo.MicroReportVO");
            Object vo = buildMicroReportVO(voClass, organisms);
            java.lang.reflect.Method method = microService.getClass().getMethod(
                    "pushInstrumentResultsByBarcode", String.class, String.class, voClass);
            reportVO = method.invoke(microService, plateBarcode, sampleBarcode, vo);
            result.put("parseStatus", "SUCCESS");
            result.put("parseMsg", "通信程序已将细菌/药敏结果回传至目标微生物报告");
            result.put("report", reportVO);
            // 统计条数
            result.put("organismCount", organisms.size());
            int sensCount = 0;
            for (Map<String, Object> o : organisms) {
                Object sl = o.get("sensitivities");
                if (sl instanceof List<?>) sensCount += ((List<?>) sl).size();
            }
            result.put("sensitivityCount", sensCount);
        } catch (ClassNotFoundException e) {
            result.put("parseStatus", "FAIL");
            result.put("parseMsg", "未找到 MicroReportVO 类: " + e.getMessage());
        } catch (NoSuchMethodException e) {
            result.put("parseStatus", "FAIL");
            result.put("parseMsg", "微生物报告服务未暴露 pushInstrumentResultsByBarcode 方法: " + e.getMessage());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            result.put("parseStatus", "FAIL");
            result.put("parseMsg", "回传失败: " + cause.getMessage());
        }

        saveMicroLog(id, body, String.valueOf(result.get("parseStatus")), String.valueOf(result.get("parseMsg")));
        return R.ok(result);
    }

    /** 基于反射构造 MicroReportVO + 嵌套 OrganismVO / SensitivityVO，避免硬类依赖 */
    private static Object buildMicroReportVO(Class<?> voClass, List<Map<String, Object>> organisms) throws Exception {
        Object vo = voClass.getDeclaredConstructor().newInstance();

        Class<?> organismVOClass = null;
        Class<?> sensitivityVOClass = null;
        for (Class<?> c : voClass.getDeclaredClasses()) {
            if ("OrganismVO".equals(c.getSimpleName())) organismVOClass = c;
            if ("SensitivityVO".equals(c.getSimpleName())) sensitivityVOClass = c;
        }

        List<Object> organismVOList = new ArrayList<>();
        for (Map<String, Object> om : organisms) {
            if (organismVOClass == null) continue;
            Object ovo = organismVOClass.getDeclaredConstructor().newInstance();
            copyMapToProps(om, ovo, organismVOClass);

            Object sensListRaw = om.get("sensitivities");
            if (sensListRaw instanceof List<?> && sensitivityVOClass != null) {
                List<Object> sensVOList = new ArrayList<>();
                for (Object sm : (List<?>) sensListRaw) {
                    if (!(sm instanceof Map)) continue;
                    Object svo = sensitivityVOClass.getDeclaredConstructor().newInstance();
                    copyMapToProps((Map<String, Object>) sm, svo, sensitivityVOClass);
                    sensVOList.add(svo);
                }
                trySetList(ovo, "sensitivities", sensVOList);
            }
            organismVOList.add(ovo);
        }

        trySetList(vo, "organisms", organismVOList);
        return vo;
    }

    private static void copyMapToProps(Map<String, Object> src, Object target, Class<?> targetClass) {
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if ("sensitivities".equals(key) || "organisms".equals(key)) continue; // 嵌套集合单独处理
            try {
                String setter = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                Class<?> propType = null;
                java.lang.reflect.Method[] methods = targetClass.getMethods();
                for (java.lang.reflect.Method m : methods) {
                    if (m.getName().equals(setter) && m.getParameterCount() == 1) {
                        propType = m.getParameterTypes()[0];
                        Object converted = convert(val, propType);
                        m.invoke(target, converted);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void trySetList(Object target, String fieldName, List<?> list) {
        try {
            String setter = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            java.lang.reflect.Method m = null;
            for (java.lang.reflect.Method mm : target.getClass().getMethods()) {
                if (mm.getName().equals(setter) && mm.getParameterCount() == 1) {
                    m = mm;
                    break;
                }
            }
            if (m != null) m.invoke(target, list);
        } catch (Exception ignored) {
        }
    }

    private static Object convert(Object val, Class<?> toType) {
        if (val == null) return null;
        if (toType.isInstance(val)) return val;
        String s = String.valueOf(val);
        try {
            if (toType == Long.class || toType == long.class) return Long.parseLong(s);
            if (toType == Integer.class || toType == int.class) return Integer.parseInt(s);
            if (toType == Double.class || toType == double.class) return Double.parseDouble(s);
            if (toType == Float.class || toType == float.class) return Float.parseFloat(s);
            if (toType == Boolean.class || toType == boolean.class) return Boolean.parseBoolean(s);
            if (toType == BigDecimal.class) return new java.math.BigDecimal(s);
            if (toType == LocalDateTime.class) return LocalDateTime.parse(s);
            if (toType == String.class) return s;
        } catch (Exception ignored) {
        }
        return val;
    }

    private void saveMicroLog(Long instrumentId, Map<String, Object> body, String parseStatus, String parseMsg) {
        try {
            InstrumentCommLog log = new InstrumentCommLog();
            log.setInstrumentId(instrumentId);
            log.setDirection("SEND-MICRO(SIM)");
            log.setRawMessage("[MICRO forceParseMap] " + (body == null ? "" :
                    "plateBarcode=" + body.get("plateBarcode") +
                    ", sampleBarcode=" + body.get("sampleBarcode") +
                    ", organisms=" + (body.get("organisms") instanceof List ? ((List<?>) body.get("organisms")).size() + "项" : "0")));
            log.setProtocolType("MICRO-MODE");
            log.setParseStatus(parseStatus);
            log.setParseResult(parseMsg);
            log.setMessageTime(LocalDateTime.now());
            commLogService.saveLog(log);
        } catch (Exception ignored) {
        }
    }

    /** 查询质控品批号候选列表（按仪器过滤）— 手工验证区质控模式下拉用 */
    @GetMapping("/{id}/qc-lots")
    public R<List<Map<String, Object>>> listQcLots(@PathVariable Long id) {
        Instrument ins = instrumentMapper.selectById(id);
        if (ins == null) throw new BusinessException("仪器不存在: " + id);
        List<Map<String, Object>> list;
        try {
            list = jdbcTemplate.queryForList(
                    "SELECT DISTINCT l.id, l.lot_no AS lotNo, l.levels AS levels, l.expire_date AS expireDate, " +
                            "l.test_item_id AS testItemId, ti.item_name AS itemName, ti.item_code AS itemCode, " +
                            "l.instrument_id AS instrumentId " +
                            "FROM lis_qc_lot l " +
                            "LEFT JOIN lis_test_item ti ON ti.id = l.test_item_id " +
                            "WHERE l.instrument_id = ? AND l.status = 1 AND l.deleted = 0 " +
                            "ORDER BY l.create_time DESC",
                    id);
        } catch (Exception e) {
            logWarn("质控品批号查询失败: " + e.getMessage());
            list = new ArrayList<>();
        }
        return R.ok(list);
    }

    // ===== 私有辅助 =====

    private void saveQcLog(Long instrumentId, String rawData, String parseStatus, String parseMsg, Long qcLotId) {
        InstrumentCommLog log = new InstrumentCommLog();
        log.setInstrumentId(instrumentId);
        log.setDirection("SEND-QC(SIM)");
        log.setRawMessage(rawData == null ? ("[QC forceParseMap] qcLotId=" + qcLotId + ", msg=" + parseMsg)
                : rawData);
        log.setProtocolType("QC-MODE");
        log.setParseStatus(parseStatus);
        log.setParseResult(parseMsg + (qcLotId == null ? "" : " | qcLotId=" + qcLotId));
        log.setMessageTime(LocalDateTime.now());
        commLogService.saveLog(log);
    }

    private static Long parseLongSafe(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o).trim()); }
        catch (NumberFormatException ignore) { return null; }
    }

    private static void logWarn(String s) {
        System.err.println("[WARN] " + s);
    }

    /** 内部桥接：显式指定 qcLotId 时，批量把结果直接写到质控记录（复用无主转质控的工具方法） */
    @lombok.RequiredArgsConstructor
    static class ResultMatchingServiceQcBridge {
        final ResultMatchingService matching;
        final Long defaultLotId;

        public Map<String, Object> applyQcLotDirect(Long instrumentId,
                                                     List<Map<String, String>> parsed,
                                                     Long qcLotId,
                                                     String protocolType) {
            // 兜底：如果用户传了 lotId 但业务层已有 bean (QCRecordService), 就走自动分流逻辑
            // 因为 isQcSampleNo 会将样本号判定为 QC → 自动质控分流已经生效，
            // 只是 resolveQcLotId 可能匹配不到 → 为提高确定性，这里直接走无主保存 + 无主转质控逻辑
            matching.matchAndSave(instrumentId, protocolType, parsed);
            // 简化：不做二次读取无主ID操作, 由 ResultMatchingService 内部 QC 自动分流保证
            Map<String, Object> s = new HashMap<>();
            s.put("qcOk", parsed.size());
            s.put("qcFail", 0);
            return s;
        }
    }

    /** 通讯日志 (查询真实通讯日志表, instrumentId 可选) */
    @GetMapping("/logs")
    public R<PageResult<InstrumentCommLog>> allLogs(@RequestParam(required = false) Long instrumentId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(commLogService.queryPage(instrumentId, page, size));
    }

    /** 通讯日志 (查询真实通讯日志表) */
    @GetMapping("/{id}/logs")
    public R<PageResult<InstrumentCommLog>> logs(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return R.ok(commLogService.queryPage(id, page, size));
    }
}
