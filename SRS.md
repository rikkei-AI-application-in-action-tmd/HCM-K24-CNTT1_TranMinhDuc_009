# SRS — Đặc tả Yêu cầu Phần mềm: Hệ thống Tích điểm Hoàn tiền (Cashback/Rewards)

## 1. Giới thiệu

### 1.1 Mục đích
Tài liệu này đặc tả chi tiết yêu cầu phần mềm (Software Requirements Specification) cho tính năng **"Tích điểm hoàn tiền" (Cashback/Rewards)** áp dụng cho Thẻ tín dụng trong hệ thống CoreBanking.

### 1.2 Phạm vi
Tính năng bao gồm:
- Mở rộng mô hình dữ liệu (thêm Entity `CreditCard`, `Transaction` và các Enum hỗ trợ).
- Xây dựng API Payment tự động tính cashback dựa trên **hạng thẻ** × **danh mục chi tiêu**.
- Thiết kế kiến trúc **Strategy Pattern** cho logic tính hoàn tiền — dễ mở rộng khi thêm hạng thẻ hoặc danh mục mới.
- Xử lý ngoại lệ chuẩn HTTP 422 (Unprocessable Entity).

### 1.3 Bối cảnh hệ thống hiện tại
Hệ thống CoreBanking đã có sẵn:
- **Entities**: `Customer`, `BankAccount` (với `AccountType`: CHECKING, SAVINGS, CREDIT).
- **Stack**: Spring Boot 3.2.4, Java 17, Gradle, MySQL, Lombok, Spring Security (JWT).
- **Package**: `com.banking.models.entities`, `com.banking.models.constant`, `com.banking.controllers`, `com.banking.advice`.

### 1.4 Thuật ngữ

| Thuật ngữ | Định nghĩa |
| :--- | :--- |
| **CardTier** | Hạng thẻ tín dụng, quyết định mức ưu đãi hoàn tiền. Hiện có 2 hạng: `STANDARD` và `PLATINUM`. |
| **SpendingCategory** | Danh mục chi tiêu của giao dịch. Hiện có 3 danh mục: `GROCERY` (Siêu thị), `TRAVEL` (Du lịch), `OTHER` (Khác). |
| **Cashback** | Số tiền hoàn trả cho khách hàng sau mỗi giao dịch, tính theo tỷ lệ phần trăm. |
| **RewardPoints** | Điểm thưởng tích lũy, quy đổi theo tỷ lệ 1 VND cashback = 1 điểm. |

---

## 2. Phân tích Nghiệp vụ

### 2.1 Yêu cầu nghiệp vụ gốc (trích Email Giám đốc Marketing)

> *"Chào team, để kích cầu mua sắm, ngân hàng sẽ ra mắt hệ thống 'Tích điểm hoàn tiền' (Cashback/Rewards) cho Thẻ tín dụng."*

Phân tích chi tiết yêu cầu:

| # | Yêu cầu nghiệp vụ | Phân loại | Giải pháp kỹ thuật |
| :-: | :--- | :--- | :--- |
| BR-01 | Thẻ tín dụng chia 2 hạng: STANDARD và PLATINUM | Data Model | Enum `CardTier` với 2 giá trị |
| BR-02 | Hệ thống tự động tính cashback khi quẹt thẻ | Business Logic | `PaymentService.processPayment()` |
| BR-03 | Tỷ lệ cashback phụ thuộc hạng thẻ + danh mục | Decision Logic | Decision Table (Section 4) + Strategy Pattern |
| BR-04 | Grocery: STANDARD 1%, PLATINUM 3% | Business Rule | `StandardCashbackStrategy`, `PlatinumCashbackStrategy` |
| BR-05 | Travel: STANDARD 0.5%, PLATINUM 5% | Business Rule | Tương tự BR-04 |
| BR-06 | Các danh mục khác: không hoàn tiền | Business Rule | Cả 2 strategy trả về 0% cho `OTHER` |
| BR-07 | API trả về tổng tiền đã trừ và số điểm hoàn nhận được | API Design | `PaymentResponse` DTO |

### 2.2 Phân tích biến đầu vào của Decision Table

Bảng quyết định (Decision Table) được xây dựng từ 2 biến đầu vào:

- **Biến 1 — CardTier**: 2 giá trị → {STANDARD, PLATINUM}
- **Biến 2 — SpendingCategory**: 3 giá trị → {GROCERY, TRAVEL, OTHER}

Tổng số tổ hợp = 2 × 3 = **6 trường hợp** (phủ hết 100% không gian quyết định, không có trường hợp nào bị bỏ sót).

### 2.3 Luồng xử lý chính (Business Flow)

```
Client gửi Request (cardId, amount, category)
         │
         ▼
    ┌─────────────────┐
    │ Validate Input  │──── category không hợp lệ? ──→ HTTP 422
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │  Tìm CreditCard │──── Không tìm thấy? ──→ HTTP 422
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │ Kiểm tra Status │──── INACTIVE? ──→ HTTP 422
    └────────┬────────┘
             │
             ▼
    ┌─────────────────────────────────┐
    │ CashbackStrategyFactory         │
    │  → getStrategy(card.cardTier)   │
    │  → strategy.calculatePercent()  │
    └────────┬────────────────────────┘
             │
             ▼
    ┌─────────────────────────────────┐
    │ Tính toán:                      │
    │  cashback = amount × % / 100   │
    │  rewardPoints = cashback        │
    │  card.rewardPoints += points    │
    └────────┬────────────────────────┘
             │
             ▼
    ┌─────────────────┐
    │  Lưu Transaction│
    │  Trả Response   │──→ HTTP 200 + PaymentResponse
    └─────────────────┘
```

---

## 3. Entity Design (Mô hình dữ liệu)

### 3.1 Entity Relationship Diagram (ERD)

```
┌──────────────┐       1        ┌──────────────┐       1        ┌──────────────┐
│   Customer   │───────────────▶│  CreditCard  │───────────────▶│ Transaction  │
│              │    has many     │              │    has many     │              │
│  id (PK)     │                │  id (PK)     │                │  id (PK)     │
│  username    │                │  cardNumber  │                │  amount      │
│  password    │                │  cardTier    │                │  category    │
│  fullName    │                │  status      │                │  cashbackAmt │
│  ...         │                │  rewardPts   │                │  rewardPts   │
│              │                │  customer_id │                │  card_id (FK)│
│              │                │  createdAt   │                │  createdAt   │
└──────────────┘                └──────────────┘                └──────────────┘
```

### 3.2 CreditCard Entity (Table: `credit_cards`)

| Trường | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | PK, Auto Increment | Khóa chính |
| `cardNumber` | `String(16)` | Unique, Not Null | Số thẻ tín dụng 16 ký tự |
| `cardTier` | `Enum(CardTier)` | Not Null | Hạng thẻ: `STANDARD` hoặc `PLATINUM` |
| `status` | `Enum(CardStatus)` | Not Null, Default `ACTIVE` | Trạng thái: `ACTIVE` hoặc `INACTIVE` |
| `rewardPoints` | `BigDecimal(19,4)` | Not Null, Default `0.0000` | Tổng điểm thưởng tích lũy |
| `customer_id` | `Long (FK)` | ManyToOne, LAZY | FK tới `Customer.id` |
| `createdAt` | `LocalDateTime` | Auto-set, Not Updatable | Thời gian tạo bản ghi |

### 3.3 Transaction Entity (Table: `transactions`)

| Trường | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | PK, Auto Increment | Khóa chính |
| `amount` | `BigDecimal(19,4)` | Not Null | Số tiền giao dịch gốc |
| `category` | `Enum(SpendingCategory)` | Not Null | Danh mục chi tiêu |
| `cashbackAmount` | `BigDecimal(19,4)` | Not Null | Số tiền cashback tính được |
| `rewardPoints` | `BigDecimal(19,4)` | Not Null | Điểm thưởng = cashbackAmount |
| `credit_card_id` | `Long (FK)` | ManyToOne, LAZY | FK tới `CreditCard.id` |
| `createdAt` | `LocalDateTime` | Auto-set, Not Updatable | Thời gian giao dịch |

### 3.4 Enum Definitions

| Enum | Giá trị | Mô tả |
| :--- | :--- | :--- |
| `CardTier` | `STANDARD`, `PLATINUM` | Phân loại hạng thẻ |
| `CardStatus` | `ACTIVE`, `INACTIVE` | Trạng thái hoạt động của thẻ |
| `SpendingCategory` | `GROCERY`, `TRAVEL`, `OTHER` | Phân loại danh mục chi tiêu |

---

## 4. Decision Table — Logic tính % Cashback

### 4.1 Bảng quyết định đầy đủ

Bảng quyết định kết hợp 2 yếu tố đầu vào (CardTier × SpendingCategory) để xác định tỷ lệ cashback:

| STT | Điều kiện 1: Hạng thẻ (`CardTier`) | Điều kiện 2: Danh mục (`SpendingCategory`) | Hành động: Tỷ lệ Cashback | Ví dụ: Amount = 1,000,000 VND |
| :-: | :--- | :--- | :-: | :--- |
| **1** | `STANDARD` | `GROCERY` (Siêu thị) | **1.0%** | Cashback = 10,000 VND → Points = +10,000 |
| **2** | `STANDARD` | `TRAVEL` (Du lịch) | **0.5%** | Cashback = 5,000 VND → Points = +5,000 |
| **3** | `STANDARD` | `OTHER` (Khác) | **0.0%** | Cashback = 0 VND → Points = +0 |
| **4** | `PLATINUM` | `GROCERY` (Siêu thị) | **3.0%** | Cashback = 30,000 VND → Points = +30,000 |
| **5** | `PLATINUM` | `TRAVEL` (Du lịch) | **5.0%** | Cashback = 50,000 VND → Points = +50,000 |
| **6** | `PLATINUM` | `OTHER` (Khác) | **0.0%** | Cashback = 0 VND → Points = +0 |

### 4.2 Phân tích so sánh giữa các hạng thẻ

| Danh mục | STANDARD | PLATINUM | Chênh lệch | Hệ số ưu đãi |
| :--- | :-: | :-: | :-: | :-: |
| `GROCERY` | 1.0% | 3.0% | +2.0% | ×3.0 |
| `TRAVEL` | 0.5% | 5.0% | +4.5% | ×10.0 |
| `OTHER` | 0.0% | 0.0% | 0.0% | — |

**Nhận xét phân tích**: Danh mục `TRAVEL` có hệ số ưu đãi chênh lệch cao nhất giữa PLATINUM và STANDARD (gấp 10 lần), cho thấy chiến lược marketing nhắm mục tiêu khuyến khích khách hàng nâng hạng thẻ PLATINUM để hưởng ưu đãi du lịch.

### 4.3 Quy tắc tính toán

**Công thức tính Cashback:**

$$\text{CashbackAmount} = \text{Amount} \times \frac{\text{CashbackPercent}}{100}$$

**Công thức quy đổi điểm thưởng:**

$$\text{RewardPoints} = \text{CashbackAmount} \quad (\text{tỷ lệ } 1:1)$$

**Cập nhật ví điểm thưởng:**

$$\text{CreditCard.rewardPoints}_{new} = \text{CreditCard.rewardPoints}_{old} + \text{RewardPoints}$$

### 4.4 Ví dụ minh hoạ chi tiết

**Kịch bản**: Khách hàng sở hữu thẻ PLATINUM (rewardPoints hiện tại = 50,000) thực hiện giao dịch mua sắm tại siêu thị với số tiền 2,000,000 VND.

| Bước | Phép tính | Kết quả |
| :--- | :--- | :--- |
| 1. Tra Decision Table | PLATINUM + GROCERY | Tỷ lệ = **3.0%** |
| 2. Tính Cashback | 2,000,000 × 3.0 / 100 | **60,000 VND** |
| 3. Quy đổi điểm | 60,000 × 1 | **60,000 điểm** |
| 4. Cập nhật ví | 50,000 + 60,000 | **110,000 điểm** |

---

## 5. API Payment Specifications

### 5.1 Endpoint

| Thuộc tính | Giá trị |
| :--- | :--- |
| **HTTP Method** | `POST` |
| **URL** | `/api/v1/payments` |
| **Content-Type** | `application/json` |
| **Authentication** | Public (`permitAll`) |

### 5.2 Request Body

```json
{
  "cardId": 1,
  "amount": 1000000,
  "category": "GROCERY"
}
```

| Field | Type | Validation | Mô tả |
| :--- | :--- | :--- | :--- |
| `cardId` | `Long` | `@NotNull` | ID thẻ tín dụng |
| `amount` | `BigDecimal` | `@NotNull`, `@Positive` | Số tiền giao dịch (> 0) |
| `category` | `String` | `@NotBlank` | Tên danh mục chi tiêu |

### 5.3 Response — Thành công (HTTP 200 OK)

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

| Field | Mô tả |
| :--- | :--- |
| `transactionId` | ID giao dịch vừa tạo |
| `amountCharged` | Số tiền giao dịch gốc |
| `cashbackAmount` | Số tiền hoàn lại (theo Decision Table) |
| `rewardPointsEarned` | Điểm thưởng nhận được trong giao dịch này |
| `totalRewardPoints` | Tổng điểm thưởng tích lũy trên thẻ sau giao dịch |

### 5.4 Response — Lỗi: Thẻ bị khóa (HTTP 422)

```json
{
  "data": null,
  "message": "Card is inactive. Payment cannot be processed.",
  "code": 422
}
```

### 5.5 Response — Lỗi: Danh mục không hợp lệ (HTTP 422)

```json
{
  "data": null,
  "message": "Invalid spending category: INVALID_CATEGORY",
  "code": 422
}
```

### 5.6 Response — Lỗi: Không tìm thấy thẻ (HTTP 422)

```json
{
  "data": null,
  "message": "Credit card not found with id: 999",
  "code": 422
}
```

---

## 6. Design Pattern — Strategy Pattern Architecture

### 6.1 Lý do chọn Strategy Pattern

| Tiêu chí | If/Else truyền thống | Strategy Pattern |
| :--- | :--- | :--- |
| Mở rộng hạng thẻ mới (VD: GOLD) | Phải sửa code logic hiện tại | Chỉ thêm 1 class mới, không sửa code cũ |
| Tuân thủ Open/Closed Principle |  Vi phạm |  Tuân thủ |
| Độ dài code khi thêm nhiều hạng | Tăng tuyến tính (if/else dài) | Không ảnh hưởng file cũ |
| Kiểm thử đơn vị (Unit Test) | Khó test riêng từng hạng | Dễ test riêng từng Strategy |

### 6.2 Class Diagram

```
    ┌────────────────────────────┐
    │   <<interface>>            │
    │   CashbackStrategy         │
    ├────────────────────────────┤
    │ + calculateCashbackPercent │
    │   (SpendingCategory)       │
    │   : BigDecimal             │
    └──────────┬─────────────────┘
               │ implements
       ┌───────┴────────┐
       │                │
       ▼                ▼
┌──────────────┐  ┌──────────────┐
│  Standard    │  │  Platinum    │
│  Cashback    │  │  Cashback    │
│  Strategy    │  │  Strategy    │
│  (@Component)│  │  (@Component)│
├──────────────┤  ├──────────────┤
│ GROCERY → 1% │  │ GROCERY → 3% │
│ TRAVEL → 0.5%│  │ TRAVEL  → 5% │
│ OTHER   → 0% │  │ OTHER   → 0% │
└──────────────┘  └──────────────┘
       ▲                ▲
       │                │
       └───────┬────────┘
               │ injects
    ┌──────────┴─────────────────┐
    │  CashbackStrategyFactory   │
    │  (@Component)              │
    ├────────────────────────────┤
    │ + getStrategy(CardTier)    │
    │   : CashbackStrategy       │
    │                            │
    │  STANDARD → Standard...    │
    │  PLATINUM → Platinum...    │
    └────────────────────────────┘
```

---

## 7. Xử lý Ngoại lệ (Exception Handling)

### 7.1 Ma trận lỗi nghiệp vụ

| # | Trường hợp vi phạm | Điều kiện kích hoạt | HTTP Status | Error Message | Thứ tự kiểm tra |
| :-: | :--- | :--- | :-: | :--- | :-: |
| E-01 | Danh mục không hợp lệ | `SpendingCategory.valueOf()` ném `IllegalArgumentException` | **422** | `"Invalid spending category: {category}"` | 1 |
| E-02 | Thẻ không tồn tại | `CreditCardRepository.findById()` trả về `Optional.empty()` | **422** | `"Credit card not found with id: {cardId}"` | 2 |
| E-03 | Thẻ bị khóa | `card.getStatus() == CardStatus.INACTIVE` | **422** | `"Card is inactive. Payment cannot be processed."` | 3 |

### 7.2 Giải thích thứ tự validation

Thứ tự kiểm tra được thiết kế theo nguyên tắc **Fail-Fast** (thất bại sớm nhất có thể):

1. **Category trước** (E-01): Kiểm tra đầu vào của client trước khi truy vấn database → tránh truy vấn DB không cần thiết nếu input đã sai.
2. **Card tồn tại** (E-02): Phải tìm thấy thẻ trước khi kiểm tra trạng thái.
3. **Card status** (E-03): Kiểm tra cuối cùng trước khi thực hiện logic nghiệp vụ.

### 7.3 Cơ chế xử lý lỗi

Tất cả lỗi nghiệp vụ đều sử dụng class `BusinessException(int code, String message)` đã có sẵn trong hệ thống. `GlobalExceptionHandler` bắt exception này và trả về `ApiResponse<Void>` với format chuẩn:

```json
{
  "data": null,
  "message": "<error message>",
  "code": 422
}
```
