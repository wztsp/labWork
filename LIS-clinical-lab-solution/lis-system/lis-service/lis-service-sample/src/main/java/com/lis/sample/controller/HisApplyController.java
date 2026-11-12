package com.lis.sample.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lis.sample.service.HisApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * HIS 检验申请接收接口(WebAPI, POST)。
 * <p>
 * HIS 推送地址: http://{LIS地址}:{端口}/api/his/apply/receive<br>
 * 令牌: 参数配置 his.apply.token 非空时, 需在请求头 X-HIS-Token 或 URL 参数 token 中携带。
 */
@RestController
@RequestMapping("/api/his/apply")
@RequiredArgsConstructor
public class HisApplyController {

    private final HisApplyService hisApplyService;

    /**
     * 接收 HIS 检验申请，落库为检验申请(待采集任务)。
     * 应答固定为 HIS 平台格式，HTTP 状态码恒为 200，业务结果看 output.ack_info.exe_status。
     */
    @PostMapping("/receive")
    public Map<String, Object> receive(@RequestBody(required = false) JsonNode body,
                                       @RequestHeader(value = "X-HIS-Token", required = false) String headerToken,
                                       @RequestParam(value = "token", required = false) String paramToken) {
        String token = (headerToken != null && !headerToken.trim().isEmpty()) ? headerToken : paramToken;
        return hisApplyService.receive(body, token);
    }

    /**
     * 接收 HIS 撤销检验申请(S4014)，作废对应的 LIS 检验申请。
     */
    @PostMapping("/cancel")
    public Map<String, Object> cancel(@RequestBody(required = false) JsonNode body,
                                      @RequestHeader(value = "X-HIS-Token", required = false) String headerToken,
                                      @RequestParam(value = "token", required = false) String paramToken) {
        String token = (headerToken != null && !headerToken.trim().isEmpty()) ? headerToken : paramToken;
        return hisApplyService.cancel(body, token);
    }
}
