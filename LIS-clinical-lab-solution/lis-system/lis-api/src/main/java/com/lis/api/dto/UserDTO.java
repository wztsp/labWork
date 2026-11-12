package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private String username;

    private String password;

    private String realName;

    private String phone;

    private String email;

    private Long departmentId;

    private List<Long> roleIds;
}
