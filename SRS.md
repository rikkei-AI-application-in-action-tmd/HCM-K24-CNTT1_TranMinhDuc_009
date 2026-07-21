  # SRS — Hệ thống Tích điểm Hoàn tiền (Cashback/Rewards)

## 1. Giới thiệu

### 1.1 Mục đích
Tài liệu này mô tả chi tiết đặc tả yêu cầu phần mềm (SRS) cho tính năng **"Tích điểm hoàn tiền" (Cashback/Rewards)** áp dụng cho Thẻ tín dụng trong hệ thống CoreBanking.

### 1.2 Phạm vi
- Thêm Entity `CreditCard` (quản lý hạng thẻ, trạng thái và ví điểm thưởng) và `Transaction` (lưu vết lịch sử giao dịch và cashback).
- Xây dựng API Payment tự động tính tiền hoàn lại (Cashback) dựa trên Hạng thẻ và Danh mục chi tiêu (Category).
- Áp dụng **Strategy Pattern** cho logic tính hoàn tiền.
- Xử lý ngoại lệ chuẩn HTTP 422 khi thẻ bị khóa hoặc danh mục không hợp lệ.

---

## 2. Entity Design

### 2.1 CreditCard Entity

| Trường | Kiểu dữ liệu | Ràng buộc / Mô tả |
| :--- | :--- | :--- |
| `id` | `Long` | Khóa chính (PK, Auto Increment) |
| `cardNumber` | `String` | Số thẻ tín dụng (Unique, 16 ký tự) |
| `cardTier` | `Enum (CardTier)` | Hạng thẻ: `STANDARD`, `PLATINUM` |
| `status` | `Enum (CardStatus)` | Trạng thái thẻ: `ACTIVE`, `INACTIVE` (Default: `ACTIVE`) |
| `rewardPoints` | `BigDecimal` | Ví điểm thưởng tích lũy (Default: `0.0000`, Precision: 19, Scale: 4) |
| `customer` | `ManyToOne` | Chủ sở hữu thẻ (Liên kết tới `Customer` Entity, `LAZY` fetch) |
| `createdAt` | `LocalDateTime` | Thời gian tạo bản ghi (`updatable = false`) |

### 2.2 Transaction Entity

| Trường | Kiểu dữ liệu | Ràng buộc / Mô tả |
| :--- | :--- | :--- |
| `id` | `Long` | Khóa chính (PK, Auto Increment) |
| `amount` | `BigDecimal` | Số tiền giao dịch (Precision: 19, Scale: 4) |
| `category` | `Enum (SpendingCategory)` | Danh mục chi tiêu: `GROCERY`, `TRAVEL`, `OTHER` |
| `cashbackAmount` | `BigDecimal` | Số tiền được hoàn lại (Precision: 19, Scale: 4) |
| `rewardPoints` | `BigDecimal` | Số điểm thưởng nhận được trong giao dịch (Precision: 19, Scale: 4) |
| `creditCard` | `ManyToOne` | Thẻ tín dụng thực hiện giao dịch (Liên kết `CreditCard`, `LAZY` fetch) |
| `createdAt` | `LocalDateTime` | Thời gian giao dịch (`updatable = false`) |

---

## 3. Decision Table — Logic tính % Cashback

| STT | Hạng thẻ (`CardTier`) | Danh mục (`SpendingCategory`) | Tỷ lệ Cashback | Ví dụ giao dịch: Amount = 1,000,000 VND |
| :-: | :--- | :--- | :-: | :--- |
| **1** | `STANDARD` | `GROCERY` (Siêu thị) | **1.0%** | Cashback = 10,000 VND, Points = +10,000 |
| **2** | `STANDARD` | `TRAVEL` (Du lịch) | **0.5%** | Cashback = 5,000 VND, Points = +5,000 |
| **3** | `STANDARD` | `OTHER` (Khác) | **0.0%** | Cashback = 0 VND, Points = +0 |
| **4** | `PLATINUM` | `GROCERY` (Siêu thị) | **3.0%** | Cashback = 30,000 VND, Points = +30,000 |
| **5** | `PLATINUM` | `TRAVEL` (Du lịch) | **5.0%** | Cashback = 50,000 VND, Points = +50,000 |
| **6** | `PLATINUM` | `OTHER` (Khác) | **0.0%** | Cashback = 0 VND, Points = +0 |

**Quy tắc nghiệp vụ:**
1. $\text{CashbackAmount} = \text{Amount} \times \left(\frac{\text{CashbackPercent}}{100}\right)$
2. Quy đổi điểm thưởng: **1 VND Cashback = 1 Điểm thưởng (`rewardPoints`)**.
3. Điểm thưởng của giao dịch mới sẽ được cộng dồn trực tiếp vào trường `rewardPoints` của thẻ tín dụng (`CreditCard`).

---

## 4. API Payment Specifications

### 4.1 Endpoint
- **HTTP Method**: `POST`
- **URL**: `/api/v1/payments`
- **Authentication**: Public (`permitAll`)

### 4.2 Request Body
```json
{
  "cardId": 1,
  "amount": 1000000,
  "category": "GROCERY"
}
```

### 4.3 Response — Thành công (HTTP 200 OK)
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

### 4.4 Response — Lỗi Thẻ bị khóa (HTTP 422 Unprocessable Entity)
```json
{
  "data": null,
  "message": "Card is inactive. Payment cannot be processed.",
  "code": 422
}
```

### 4.5 Response — Lỗi Danh mục không hợp lệ (HTTP 422 Unprocessable Entity)
```json
{
  "data": null,
  "message": "Invalid spending category: INVALID_CATEGORY",
  "code": 422
}
```

---

## 5. Design Pattern Architecture

Hệ thống áp dụng **Strategy Pattern** để đóng gói và linh hoạt mở rộng thuật toán tính toán % hoàn tiền theo từng hạng thẻ:

- **`CashbackStrategy`** *(Interface)*: Định nghĩa phương thức `calculateCashbackPercent(SpendingCategory category)`.
- **`StandardCashbackStrategy`** *(Component)*: Triển khai logic tính cho hạng thẻ `STANDARD`.
- **`PlatinumCashbackStrategy`** *(Component)*: Triển khai logic tính cho hạng thẻ `PLATINUM`.
- **`CashbackStrategyFactory`** *(Component)*: Nhận `CardTier` và trả về `CashbackStrategy` tương ứng.

---

## 6. Matrix Xử lý Ngoại lệ (Exception Handling)

| Trường hợp vi phạm | Nguyên nhân | HTTP Status | Business Error Message |
| :--- | :--- | :-: | :--- |
| **Không tìm thấy thẻ** | `cardId` không tồn tại trong cơ sở dữ liệu | **422** | `"Credit card not found with id: {cardId}"` |
| **Thẻ bị khóa** | Thẻ tín dụng có `status = INACTIVE` | **422** | `"Card is inactive. Payment cannot be processed."` |
| **Danh mục không hợp lệ** | `category` truyền vào không thuộc `SpendingCategory` | **422** | `"Invalid spending category: {category}"` |
