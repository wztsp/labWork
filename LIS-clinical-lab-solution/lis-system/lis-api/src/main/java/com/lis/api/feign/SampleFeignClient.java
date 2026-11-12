package com.lis.api.feign;

import com.lis.api.dto.SampleDTO;
import com.lis.api.vo.SampleVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "lis-service-sample", path = "/api/sample")
public interface SampleFeignClient {

    @GetMapping("/{id}")
    SampleVO getById(@PathVariable Long id);

    @GetMapping("/barcode/{barcode}")
    SampleVO getByBarcode(@PathVariable String barcode);

    @PostMapping
    SampleVO create(@RequestBody SampleDTO dto);

    @PutMapping("/receive")
    SampleVO receive(@RequestBody com.lis.api.dto.SampleReceiveDTO dto);

    @GetMapping("/list")
    List<SampleVO> list(@RequestParam(required = false) Integer status);
}
