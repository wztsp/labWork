package com.lis.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 请求日志过滤器
 * <p>
 * 记录请求方法、路径、耗时。
 */
@Component
public class LogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LogFilter.class);

    private static final String START_TIME_ATTR = "logFilterStartTime";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 需要脱敏的查询参数名 */
    private static final Set<String> SENSITIVE_PARAMS = new HashSet<>(Arrays.asList(
            "token", "access_token", "password", "pwd", "secret", "authorization", "sign", "idCard", "id_card"
    ));

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String query = redactQuery(request.getURI().getQuery());
        String traceId = UUID.randomUUID().toString().replace("-", "");

        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        // 注入 traceId 传递给下游服务
        ServerHttpRequest mutated = request.mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();

        long start = System.currentTimeMillis();
        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signalType -> {
                    Object attr = exchange.getAttributes().get(START_TIME_ATTR);
                    long startTime = attr == null ? start : (long) attr;
                    long cost = System.currentTimeMillis() - startTime;
                    int status = exchange.getResponse().getStatusCode() == null
                            ? 0 : exchange.getResponse().getStatusCode().value();
                    log.info("[GATEWAY] traceId={} {} {} query=[{}] status={} cost={}ms",
                            traceId, method, path, query, status, cost);
                });
    }

    /**
     * 对查询串中的敏感参数做脱敏, 防止 token/身份证等明文进入日志
     */
    private String redactQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        String[] pairs = rawQuery.split("&");
        StringBuilder sb = new StringBuilder();
        for (String pair : pairs) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            int eq = pair.indexOf('=');
            if (eq > 0 && SENSITIVE_PARAMS.contains(pair.substring(0, eq))) {
                sb.append(pair, 0, eq).append("=***");
            } else {
                sb.append(pair);
            }
        }
        return sb.toString();
    }

    @Override
    public int getOrder() {
        // 在鉴权过滤器之后执行
        return -50;
    }
}
