package com.lis.api.feign;

import com.lis.api.dto.ReferenceRangeDTO;
import com.lis.api.vo.TestItemVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@FeignClient(name = "lis-service-base", path = "/api/base")
public interface BaseFeignClient {

    @GetMapping("/item/code/{code}")
    TestItemVO getItemByCode(@PathVariable String code);

    @GetMapping("/reference-range")
    ReferenceRangeDTO getReferenceRange(@RequestParam Long itemId,
                                        @RequestParam Integer gender,
                                        @RequestParam BigDecimal age);
}
