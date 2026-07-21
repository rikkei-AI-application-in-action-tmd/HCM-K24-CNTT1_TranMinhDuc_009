# SRS — Hệ thống Tích điểm Hoàn tiền (Cashback/Rewards)

## 1. Giới thiệu

### 1.1 Mục đích
Tài liệu này mô tả yêu cầu hệ thống cho tính năng "Tích điểm hoàn tiền" (Cashback/Rewards) áp dụng cho Thẻ tín dụng trong hệ thống CoreBanking.

### 1.2 Phạm vi
- Thêm entity `CreditCard` (có hạng thẻ) và `Transaction` (có danh mục chi tiêu).
- Xây dựng API Payment tự động tính tiền hoàn lại (Cashback) dựa trên hạng thẻ và danh mục chi tiêu.
- Xử lý ngoại lệ khi thẻ bị khóa hoặc danh mục không hợp lệ.

## 2. Entity Design

### 2.1 CreditCard Entity
| Trường | Kiểu | Mô tả |
|--------|------|-------|
| id | Long (PK, auto-gen) | Khóa chính |
| cardNumber | String (unique, 16 ký tự) | Số thẻ tín dụng |
| cardTier | Enum: `STANDARD`, `PLATINUM` | Hạng thẻ |
| status | Enum: `ACTIVE`, `INACTIVE` | Trạng thái thẻ |
| rewardPoints | BigDecimal (default 0) | Ví điểm thưởng tích lũy |
| customer | ManyToOne → Customer | Chủ thẻ |
| createdAt | LocalDateTime | Ngày tạo |

### 2.2 Transaction Entity
| Trường | Kiểu | Mô tả |
|--------|------|-------|
| id | Long (PK, auto-gen) | Khóa chính |
| amount | BigDecimal | Số tiền giao dịch |
| category | Enum: `GROCERY`, `TRAVEL`, `OTHER` | Danh mục chi tiêu |
| cashbackAmount | BigDecimal | Tiền hoàn lại |
| rewardPoints | BigDecimal | Điểm thưởng nhận được |
| creditCard | ManyToOne → CreditCard | Thẻ thực hiện giao dịch |
| createdAt | LocalDateTime | Thời gian giao dịch |

## 3. Decision Table — Logic tính % Cashback

| Hạng thẻ (CardTier) | Danh mục (Category) | % Cashback | Ví dụ: Amount = 1,000,000 VND |
|----------------------|---------------------|------------|-------------------------------|
| STANDARD | GROCERY | 1% | Cashback = 10,000 VND |
| STANDARD | TRAVEL | 0.5% | Cashback = 5,000 VND |
| STANDARD | OTHER | 0% | Cashback = 0 VND |
| PLATINUM | GROCERY | 3% | Cashback = 30,000 VND |
| PLATINUM | TRAVEL | 5% | Cashback = 50,000 VND |
| PLATINUM | OTHER | 0% | Cashback = 0 VND |

**Quy tắc:**
- Cashback = amount × (% Cashback / 100)
- Điểm thưởng (rewardPoints) = Cashback (1 VND cashback = 1 điểm)
- Điểm thưởng được cộng dồn vào trường `rewardPoints` của CreditCard.

## 4. API Payment

### 4.1 Endpoint
```
POST /api/v1/payments
```

### 4.2 Request Body
```json
{
  "cardId": 1,
  "amount": 1000000,
  "category": "GROCERY"
}
```

### 4.3 Response thành công (200 OK)
```json
{
  "data": {
    "transactionId": 1,
    "amountCharged": 1000000,
    "cashbackAmount": 10000,
    "rewardPointsEarned": 10000,
    "totalRewardPoints": 10000
  },
  "message": "Payment processed successfully",
  "code": 200
}
```

### 4.4 Response lỗi — Thẻ bị khóa (422)
```json
{
  "data": null,
  "message": "Card is inactive. Payment cannot be processed.",
  "code": 422
}
```

### 4.5 Response lỗi — Category không hợp lệ (422)
```json
{
  "data": null,
  "message": "Invalid spending category: INVALID_CATEGORY",
  "code": 422
}
```

## 5. Design Pattern

Sử dụng **Strategy Pattern** để tách logic tính cashback theo hạng thẻ:

- `CashbackStrategy` (interface): method `calculateCashbackPercent(SpendingCategory category)` → trả `BigDecimal`.
- `StandardCashbackStrategy`: implement cho STANDARD (GROCERY→1%, TRAVEL→0.5%, OTHER→0%).
- `PlatinumCashbackStrategy`: implement cho PLATINUM (GROCERY→3%, TRAVEL→5%, OTHER→0%).
- `CashbackStrategyFactory`: nhận `CardTier` → trả `CashbackStrategy` tương ứng.

## 6. Xử lý ngoại lệ

| Điều kiện | HTTP Status | Message |
|-----------|-------------|---------|
| CreditCard không tìm thấy | 422 | "Credit card not found with id: {cardId}" |
| CreditCard có status = INACTIVE | 422 | "Card is inactive. Payment cannot be processed." |
| Category không parse được từ request | 422 | "Invalid spending category: {category}" |
