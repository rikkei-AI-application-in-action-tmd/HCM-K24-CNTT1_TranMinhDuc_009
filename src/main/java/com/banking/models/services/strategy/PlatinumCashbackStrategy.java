package com.banking.models.services.strategy;

import com.banking.models.constant.SpendingCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PlatinumCashbackStrategy implements CashbackStrategy {

    @Override
    public BigDecimal calculateCashbackPercent(SpendingCategory category) {
        return switch (category) {
            case GROCERY -> new BigDecimal("3");
            case TRAVEL -> new BigDecimal("5");
            case OTHER -> BigDecimal.ZERO;
        };
    }
}
