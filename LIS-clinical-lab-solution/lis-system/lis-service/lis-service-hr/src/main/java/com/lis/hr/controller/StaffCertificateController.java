package com.lis.hr.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.hr.entity.StaffCertificate;
import com.lis.hr.service.CertificateAttachmentService;
import com.lis.hr.service.StaffCertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 资质证书 Controller
 */
@RestController
@RequestMapping("/api/hr/certificates")
@RequiredArgsConstructor
public class StaffCertificateController {

    private final StaffCertificateService certificateService;
    private final CertificateAttachmentService attachmentService;

    /**
     * 分页查询全部证书 (前端管理页面用)
     */
    @GetMapping
    public R<PageResult<StaffCertificate>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String staffName,
            @RequestParam(required = false) String certName,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<StaffCertificate> wrapper = new LambdaQueryWrapper<>();

        // 按员工姓名搜索 (通过关联查询)
        if (StringUtils.hasText(staffName)) {
            wrapper.apply("staff_id IN (SELECT id FROM lis_staff WHERE name LIKE CONCAT('%', {0}, '%'))", staffName);
        }
        // 按证书名称搜索
        if (StringUtils.hasText(certName)) {
            wrapper.like(StaffCertificate::getCertName, certName);
        }
        // 按状态筛选
        if (status != null) {
            wrapper.eq(StaffCertificate::getStatus, status);
        }
        wrapper.orderByDesc(StaffCertificate::getCreateTime);

        Page<StaffCertificate> page = new Page<>(current, size);
        certificateService.page(page, wrapper);

        // 填充附件数量
        for (StaffCertificate cert : page.getRecords()) {
            int count = attachmentService.countByCertificateId(cert.getId());
            cert.setAttachmentCount(count);
        }

        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/staff/{staffId}")
    public R<List<StaffCertificate>> listByStaff(@PathVariable Long staffId) {
        List<StaffCertificate> certs = certificateService.listByStaffId(staffId);
        for (StaffCertificate cert : certs) {
            int count = attachmentService.countByCertificateId(cert.getId());
            cert.setAttachmentCount(count);
        }
        return R.ok(certs);
    }

    @GetMapping("/expiring")
    public R<List<StaffCertificate>> listExpiring(@RequestParam(defaultValue = "30") int withinDays) {
        List<StaffCertificate> certs = certificateService.listExpiring(withinDays);
        for (StaffCertificate cert : certs) {
            int count = attachmentService.countByCertificateId(cert.getId());
            cert.setAttachmentCount(count);
        }
        return R.ok(certs);
    }

    @GetMapping("/{id}")
    public R<StaffCertificate> getById(@PathVariable Long id) {
        StaffCertificate cert = certificateService.getById(id);
        if (cert != null) {
            int count = attachmentService.countByCertificateId(id);
            cert.setAttachmentCount(count);
        }
        return R.ok(cert);
    }

    @PostMapping
    public R<StaffCertificate> create(@RequestBody StaffCertificate certificate) {
        return R.ok(certificateService.create(certificate));
    }

    @PutMapping
    public R<StaffCertificate> update(@RequestBody StaffCertificate certificate) {
        return R.ok(certificateService.update(certificate));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        certificateService.delete(id);
        return R.ok();
    }
}
