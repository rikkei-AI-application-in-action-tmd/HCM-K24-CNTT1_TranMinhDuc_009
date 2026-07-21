package com.banking.models.services.strategy;

import com.banking.models.constant.SpendingCategory;

import java.math.BigDecimal;

public interface CashbackStrategy {
    /**
     * Trả về phần trăm cashback dưới dạng BigDecimal.
     * Ví dụ: 1% → trả về BigDecimal("1"), 0.5% → trả về BigDecimal("0.5")
     */
    BigDecimal calculateCashbackPercent(SpendingCategory category);
}
