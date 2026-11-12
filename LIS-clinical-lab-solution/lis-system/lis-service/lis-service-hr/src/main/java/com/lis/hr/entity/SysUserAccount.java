package com.lis.hr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统账号投影实体 (B-4 离职回收)。
 * <p>
 * 仅承载 hr 模块停用系统账号所需的 3 个字段 (id / username / status),
 * 与 system 模块的 {@code SysUser} 同表 ({@code sys_user}) 但彼此<b>解耦</b> ——
 * hr 不直接依赖 system 模块, 只同库直写 sys_user 的状态位与撤销标记。
 *
 * @see com.lis.hr.service.impl.AccountRevokeServiceImpl
 */
@TableName("sys_user")
public class SysUserAccount {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    /** 0停用 1启用 (与 SysUser.status 语义一致) */
    private Integer status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

}
