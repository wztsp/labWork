package com.lis.common.security;

/**
 * 安全上下文 - 通过 ThreadLocal 存储当前登录用户信息
 */
public class SecurityContext {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        LoginUser user = get();
        return user != null ? user.getUserId() : null;
    }

    public static String getUsername() {
        LoginUser user = get();
        return user != null ? user.getUsername() : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 登录用户信息
     */
    public static class LoginUser {
        private Long userId;
        private String username;
        private String realName;
        private Long departmentId;
        private String department;
        private java.util.List<String> permissions;

        public LoginUser() {}

        public LoginUser(Long userId, String username, String realName) {
            this.userId = userId;
            this.username = username;
            this.realName = realName;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public java.util.List<String> getPermissions() { return permissions; }
        public void setPermissions(java.util.List<String> permissions) { this.permissions = permissions; }
    }
}
