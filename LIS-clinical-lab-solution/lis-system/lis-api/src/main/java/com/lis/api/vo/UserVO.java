package com.lis.api.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private String email;

    private Long departmentId;

    private String departmentName;

    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private List<String> roles;
}
