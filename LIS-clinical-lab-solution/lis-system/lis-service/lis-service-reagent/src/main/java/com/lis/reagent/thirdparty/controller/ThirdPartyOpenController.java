package com.lis.reagent.thirdparty.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.R;
import com.lis.reagent.thirdparty.entity.ThirdPartyConfig;
import com.lis.reagent.thirdparty.service.ThirdPartyInboundService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 三方卫材入站开放接口（对方调用我们）。
 *
 * <p>鉴权：请求头 {@code X-Third-Token} 必须与对接配置中的入站 Token 一致，
 * 且对接配置需开启入站开关。</p>
 */
@RestController
@RequestMapping("/api/reagent/open/thirdparty")
@RequiredArgsConstructor
public class ThirdPartyOpenController {

    private static final String TOKEN_HEADER = "X-Third-Token";

    private final ThirdPartyInboundService inboundService;
    private final ObjectMapper objectMapper;

    /** 到货回传：生成待验收入库单。 */
    @PostMapping("/{partnerCode}/delivery")
    public R<Map<String, Object>> delivery(@PathVariable String partnerCode,
                                           @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                           @RequestBody String body,
                                           HttpServletRequest request) {
        ThirdPartyConfig config = inboundService.authorize(partnerCode, token);
        JsonNode node = parse(body);
        return R.ok(inboundService.receiveDelivery(config, node, body, request.getRequestURI()));
    }

    /** 订单状态回传。 */
    @PostMapping("/{partnerCode}/order-status")
    public R<Map<String, Object>> orderStatus(@PathVariable String partnerCode,
                                              @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                              @RequestBody String body,
                                              HttpServletRequest request) {
        ThirdPartyConfig config = inboundService.authorize(partnerCode, token);
        JsonNode node = parse(body);
        return R.ok(inboundService.updateOrderStatus(config, node, body, request.getRequestURI()));
    }

    /** 供对方拉取本系统采购订单。 */
    @GetMapping("/{partnerCode}/orders")
    public R<List<Map<String, Object>>> orders(@PathVariable String partnerCode,
                                               @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                               @RequestParam(required = false) Integer status,
                                               HttpServletRequest request) {
        ThirdPartyConfig config = inboundService.authorize(partnerCode, token);
        return R.ok(inboundService.listOrders(config, status, request.getRequestURI()));
    }

    /** 供对方拉取本系统试剂主数据。 */
    @GetMapping("/{partnerCode}/reagents")
    public R<List<Map<String, Object>>> reagents(@PathVariable String partnerCode,
                                                 @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                                 HttpServletRequest request) {
        ThirdPartyConfig config = inboundService.authorize(partnerCode, token);
        return R.ok(inboundService.listReagents(config, request.getRequestURI()));
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BusinessException("请求体不是合法 JSON: " + e.getMessage());
        }
    }
}
