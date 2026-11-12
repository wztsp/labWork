package com.lis.reagent.config;

import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把业务异常统一转换为 R 响应体，避免直接抛 500 堆栈。
 * 独立部署时原工程的公共异常处理器不在依赖内，故在此补齐。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValidation(Exception e) {
        String msg = e instanceof MethodArgumentNotValidException manv
                ? manv.getBindingResult().getFieldError() != null
                    ? manv.getBindingResult().getFieldError().getDefaultMessage()
                    : "参数校验失败"
                : "参数校验失败";
        return R.fail(400, msg);
    }

    /**
     * 静态资源缺失（如 /favicon.ico）不应当作系统异常记录，直接返回 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNoResource(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getResourcePath());
    }

    @ExceptionHandler(RuntimeException.class)
    public R<Void> handleRuntime(RuntimeException e) {
        log.error("运行时异常", e);
        return R.fail(500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(500, "系统异常，请联系管理员");
    }
}
