package com.banking.models.services.strategy;

import com.banking.models.constant.CardTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashbackStrategyFactory {

    private final StandardCashbackStrategy standardCashbackStrategy;
    private final PlatinumCashbackStrategy platinumCashbackStrategy;

    public CashbackStrategy getStrategy(CardTier tier) {
        return switch (tier) {
            case STANDARD -> standardCashbackStrategy;
            case PLATINUM -> platinumCashbackStrategy;
        };
    }
}
