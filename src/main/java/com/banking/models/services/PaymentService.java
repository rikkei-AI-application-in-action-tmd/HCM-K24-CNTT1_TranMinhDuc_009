package com.banking.models.services;

import com.banking.exceptions.BusinessException;
import com.banking.models.constant.CardStatus;
import com.banking.models.constant.SpendingCategory;
import com.banking.models.dto.PaymentRequest;
import com.banking.models.dto.PaymentResponse;
import com.banking.models.entities.CreditCard;
import com.banking.models.entities.Transaction;
import com.banking.models.repositories.CreditCardRepository;
import com.banking.models.repositories.TransactionRepository;
import com.banking.models.services.strategy.CashbackStrategy;
import com.banking.models.services.strategy.CashbackStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final CreditCardRepository creditCardRepository;
    private final TransactionRepository transactionRepository;
    private final CashbackStrategyFactory cashbackStrategyFactory;

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        // 1. Parse & validate category
        SpendingCategory category;
        try {
            category = SpendingCategory.valueOf(request.getCategory().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(422, "Invalid spending category: " + request.getCategory());
        }

        // 2. Tìm thẻ tín dụng
        CreditCard card = creditCardRepository.findById(request.getCardId())
                .orElseThrow(() -> new BusinessException(422, "Credit card not found with id: " + request.getCardId()));

        // 3. Kiểm tra trạng thái thẻ
        if (card.getStatus() == CardStatus.INACTIVE) {
            throw new BusinessException(422, "Card is inactive. Payment cannot be processed.");
        }

        // 4. Lấy strategy theo hạng thẻ và tính cashback
        CashbackStrategy strategy = cashbackStrategyFactory.getStrategy(card.getCardTier());
        BigDecimal cashbackPercent = strategy.calculateCashbackPercent(category);
        BigDecimal cashbackAmount = request.getAmount()
                .multiply(cashbackPercent)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        // 5. Điểm thưởng = cashback amount (1 VND = 1 điểm)
        BigDecimal rewardPointsEarned = cashbackAmount;

        // 6. Cộng điểm vào ví thưởng của thẻ (JPA dirty checking sẽ tự động lưu)
        card.setRewardPoints(card.getRewardPoints().add(rewardPointsEarned));

        // 7. Lưu giao dịch
        Transaction transaction = Transaction.builder()
                .amount(request.getAmount())
                .category(category)
                .cashbackAmount(cashbackAmount)
                .rewardPoints(rewardPointsEarned)
                .creditCard(card)
                .build();
        Transaction savedTransaction = transactionRepository.save(transaction);

        // 8. Build response
        return PaymentResponse.builder()
                .transactionId(savedTransaction.getId())
                .amountCharged(request.getAmount())
                .cashbackAmount(cashbackAmount)
                .rewardPointsEarned(rewardPointsEarned)
                .totalRewardPoints(card.getRewardPoints())
                .build();
    }
}
