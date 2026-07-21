package com.banking.models.dto;

import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PaymentResponse {
    private Long transactionId;
    private BigDecimal amountCharged;
    private BigDecimal cashbackAmount;
    private BigDecimal rewardPointsEarned;
    private BigDecimal totalRewardPoints;
}
