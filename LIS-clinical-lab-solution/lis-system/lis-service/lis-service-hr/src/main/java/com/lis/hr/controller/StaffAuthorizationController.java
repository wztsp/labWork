package com.lis.hr.controller;

import com.lis.common.core.result.R;
import com.lis.hr.entity.StaffAuthorization;
import com.lis.hr.service.StaffAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位授权 Controller
 */
@RestController
@RequestMapping("/api/hr/authorizations")
@RequiredArgsConstructor
public class StaffAuthorizationController {

    private final StaffAuthorizationService authorizationService;

    /**
     * 全部授权记录 (管理页面用)
     */
    @GetMapping
    public R<List<StaffAuthorization>> listAll() {
        return R.ok(authorizationService.list());
    }

    @GetMapping("/staff/{staffId}")
    public R<List<StaffAuthorization>> listByStaff(@PathVariable Long staffId) {
        return R.ok(authorizationService.listByStaffId(staffId));
    }

    /**
     * 校验授权
     */
    @GetMapping("/check")
    public R<Boolean> check(@RequestParam Long staffId,
                             @RequestParam String authType,
                             @RequestParam(required = false) String scope) {
        return R.ok(authorizationService.hasAuthorization(staffId, authType, scope));
    }

    @PostMapping
    public R<StaffAuthorization> create(@RequestBody StaffAuthorization authorization) {
        return R.ok(authorizationService.create(authorization));
    }

    @PutMapping
    public R<StaffAuthorization> update(@RequestBody StaffAuthorization authorization) {
        return R.ok(authorizationService.update(authorization));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        authorizationService.delete(id);
        return R.ok();
    }
}
