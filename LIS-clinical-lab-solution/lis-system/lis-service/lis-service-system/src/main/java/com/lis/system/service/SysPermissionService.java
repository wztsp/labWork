package com.lis.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.system.entity.SysPermission;

import java.util.List;

/**
 * 权限服务接口 - 权限树管理
 */
public interface SysPermissionService extends IService<SysPermission> {

    /**
     * 权限树节点
     */
    class PermissionNode {
        private Long id;
        private Long parentId;
        private String permCode;
        private String permName;
        private Integer permType;
        private String path;
        private String component;
        private String icon;
        private Integer sortOrder;
        private Integer status;
        private List<PermissionNode> children;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getPermCode() { return permCode; }
        public void setPermCode(String permCode) { this.permCode = permCode; }
        public String getPermName() { return permName; }
        public void setPermName(String permName) { this.permName = permName; }
        public Integer getPermType() { return permType; }
        public void setPermType(Integer permType) { this.permType = permType; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public List<PermissionNode> getChildren() { return children; }
        public void setChildren(List<PermissionNode> children) { this.children = children; }
    }

    /**
     * 构建权限树
     */
    List<PermissionNode> tree();

    /**
     * 新增权限
     */
    SysPermission create(SysPermission permission);

    /**
     * 修改权限
     */
    void update(SysPermission permission);

    /**
     * 删除权限
     */
    void delete(Long id);
}
