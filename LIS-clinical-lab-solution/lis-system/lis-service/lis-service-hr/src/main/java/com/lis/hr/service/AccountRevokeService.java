package com.lis.hr.service;

/**
 * 系统账号撤销服务 (B-4 离职回收)。
 * <p>
 * <b>接口隔离</b>: hr 仅依赖此接口, 具体实现 (本轮回同库直写 sys_user) 可日后替换为 RPC 调用,
 * 调用方无感知。
 */
public interface AccountRevokeService {

    /**
     * 撤销 (停用) 指定系统账号。
     * <p>
     * 实现须保证<b>幂等</b> (已停用则跳过 DB 写) 与<b>降级</b> (任何异常不得抛出, 仅记录日志),
     * 以免阻断调用方主流程 (如离职登记)。
     *
     * @param userId 系统用户 id (sys_user.id), 非空
     * @param reason 撤销原因, 仅用于日志审计 (如 "离职")
     */
    void revoke(Long userId, String reason);
}
