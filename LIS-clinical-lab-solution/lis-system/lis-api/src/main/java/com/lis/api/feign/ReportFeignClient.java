package com.lis.api.feign;

import com.lis.api.dto.ReportAuditDTO;
import com.lis.api.vo.ReportVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "lis-service-report", path = "/api/report")
public interface ReportFeignClient {

    @GetMapping("/{id}")
    ReportVO getById(@PathVariable Long id);

    @GetMapping("/sample/{sampleId}")
    ReportVO getBySampleId(@PathVariable Long sampleId);

    @PostMapping("/audit")
    ReportVO audit(@RequestBody ReportAuditDTO dto);
}
