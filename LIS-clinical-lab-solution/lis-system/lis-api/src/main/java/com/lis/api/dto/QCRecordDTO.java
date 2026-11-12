package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QCRecordDTO {

    private Long qcLotId;

    private Long testItemId;

    private Long instrumentId;

    private BigDecimal measureValue;

    private LocalDateTime measureTime;

    private Long operatorId;
}
