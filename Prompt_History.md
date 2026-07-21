# Prompt History — Hệ thống Tích điểm Hoàn tiền (Cashback/Rewards)

Tài liệu ghi lại toàn bộ các prompt đã sử dụng với AI để xây dựng tính năng Cashback/Rewards cho hệ thống CoreBanking. Mỗi prompt tuân theo format 5 phần (Vai trò, Bối cảnh & Nhiệm vụ, Yêu cầu, Định dạng đầu ra, Kiểm tra lại).

---

## Prompt 1: Phân tích nghiệp vụ & viết SRS

```
[VAI TRÒ] Bạn là một Business Analyst chuyên phân tích hệ thống ngân hàng lõi (Core Banking), có kinh nghiệm viết tài liệu đặc tả yêu cầu phần mềm (SRS) cho các tính năng liên quan đến thẻ tín dụng và chương trình khách hàng thân thiết.

[BỐI CẢNH & NHIỆM VỤ] Ngân hàng cần ra mắt hệ thống "Tích điểm hoàn tiền" (Cashback/Rewards) cho Thẻ tín dụng. Hệ thống CoreBanking hiện tại đã có các entity: Customer, BankAccount (với AccountType gồm CHECKING, SAVINGS, CREDIT). Yêu cầu nghiệp vụ như sau:
- Thẻ tín dụng chia 2 hạng: STANDARD và PLATINUM.
- Khi khách hàng quẹt thẻ (thực hiện API Payment), hệ thống tự động tính tiền hoàn lại (Cashback) dựa trên Hạng thẻ và Danh mục chi tiêu (Category).
- Logic bắt buộc:
  + Danh mục "Siêu thị" (Grocery): STANDARD hoàn 1%, PLATINUM hoàn 3%.
  + Danh mục "Du lịch" (Travel): STANDARD hoàn 0.5%, PLATINUM hoàn 5%.
  + Các danh mục khác: Không hoàn tiền.
- API Payment phải trả về tổng tiền đã trừ và số điểm hoàn nhận được trong giao dịch đó.
Nhiệm vụ: Viết tài liệu SRS đầy đủ cho tính năng này, bao gồm thiết kế entity CreditCard (chứa hạng thẻ dạng Enum và trạng thái thẻ) và Transaction (chứa danh mục chi tiêu), xây dựng Bảng quyết định (Decision Table) cho logic tính % Cashback kết hợp Hạng thẻ và Danh mục, mô tả API Payment (endpoint, request/response format), và liệt kê các trường hợp xử lý ngoại lệ (thẻ bị khóa, danh mục không hợp lệ).

[YÊU CẦU]
- Decision Table phải có đủ 6 tổ hợp (2 hạng × 3 danh mục) với cột ví dụ minh hoạ số tiền cụ thể.
- Entity design phải chỉ rõ kiểu dữ liệu từng trường (dùng BigDecimal cho tiền tệ, Enum cho hạng thẻ/trạng thái/danh mục).
- CreditCard entity phải có trường rewardPoints (BigDecimal) để cộng dồn điểm thưởng sau mỗi giao dịch.
- Mô tả rõ mối quan hệ giữa các entity (CreditCard ManyToOne Customer, Transaction ManyToOne CreditCard).
- API Payment: endpoint POST /api/v1/payments, request nhận cardId + amount + category, response trả transactionId + amountCharged + cashbackAmount + rewardPointsEarned + totalRewardPoints.
- Xử lý ngoại lệ: thẻ không tìm thấy → 422, thẻ INACTIVE → 422, category không hợp lệ → 422. Mô tả rõ HTTP status code và message cho từng trường hợp.
- Đề xuất Design Pattern phù hợp (Strategy Pattern) để tách logic tính cashback theo hạng thẻ, tránh tổ hợp if/else dài.

[ĐỊNH DẠNG ĐẦU RA] File Markdown (.md) có cấu trúc rõ ràng với các mục đánh số: Giới thiệu, Entity Design (bảng mô tả từng trường), Decision Table (bảng 6 dòng), API Payment (endpoint + request/response JSON mẫu), Design Pattern, Xử lý ngoại lệ (bảng liệt kê điều kiện, HTTP status, message).

[KIỂM TRA LẠI] Đọc lại Decision Table — có đúng 6 tổ hợp không, tỷ lệ % có khớp với yêu cầu nghiệp vụ đã nêu không? Các entity có trường nào thiếu so với yêu cầu (ví dụ: CreditCard thiếu status, Transaction thiếu cashbackAmount) không?
```

---

## Prompt 2: Thiết kế Entity và Enum cho CreditCard, Transaction

```
[VAI TRÒ] Bạn là một Spring Boot developer chuyên thiết kế JPA Entity cho hệ thống Core Banking, quen thuộc với Lombok, Jakarta Persistence, và best practices cho mô hình dữ liệu tài chính.

[BỐI CẢNH & NHIỆM VỤ] Dự án CoreBanking hiện tại (Spring Boot 3.2.4, Java 17, Gradle, MySQL, Lombok) đã có các entity Customer và BankAccount. Cấu trúc package hiện tại:
- Entity: com.banking.models.entities (BankAccount.java, Customer.java)
- Enum: com.banking.models.constant (CustomerStatus.java)
- Repository: com.banking.models.repositories (BankAccountRepository, CustomerRepository)
Pattern hiện tại: Entity dùng @Builder, @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor; dùng BigDecimal cho tiền tệ với precision=19, scale=4; @PrePersist cho createdAt; enum dùng @Enumerated(EnumType.STRING).
Nhiệm vụ: Tạo 3 enum mới (CardTier, CardStatus, SpendingCategory) và 2 entity mới (CreditCard, Transaction) cùng 2 repository tương ứng, tuân thủ đúng pattern code hiện tại.

[YÊU CẦU]
- Enum CardTier: STANDARD, PLATINUM. Đặt tại com.banking.models.constant.
- Enum CardStatus: ACTIVE, INACTIVE. Đặt tại com.banking.models.constant.
- Enum SpendingCategory: GROCERY, TRAVEL, OTHER. Đặt tại com.banking.models.constant.
- Entity CreditCard (table: credit_cards): id (Long PK auto), cardNumber (String unique 16 ký tự), cardTier (Enum CardTier), status (Enum CardStatus, default ACTIVE), rewardPoints (BigDecimal default ZERO, precision 19 scale 4), customer (ManyToOne LAZY → Customer), createdAt (LocalDateTime, @PrePersist).
- Entity Transaction (table: transactions): id (Long PK auto), amount (BigDecimal, precision 19 scale 4), category (Enum SpendingCategory), cashbackAmount (BigDecimal, precision 19 scale 4), rewardPoints (BigDecimal, precision 19 scale 4), creditCard (ManyToOne LAZY → CreditCard), createdAt (LocalDateTime, @PrePersist).
- Cả 2 entity đều dùng @Builder, @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor giống BankAccount hiện tại.
- Repository: CreditCardRepository extends JpaRepository<CreditCard, Long>, TransactionRepository extends JpaRepository<Transaction, Long>. Đặt tại com.banking.models.repositories.
- Không import thừa, không thêm method ngoài yêu cầu.

[ĐỊNH DẠNG ĐẦU RA] Mỗi file Java là một code block riêng biệt, bắt đầu bằng comment ghi rõ đường dẫn file (ví dụ: // src/main/java/com/banking/models/constant/CardTier.java). Tổng cộng 7 code block cho 7 file: 3 enum + 2 entity + 2 repository.

[KIỂM TRA LẠI] So sánh annotation và cách khai báo trường của CreditCard/Transaction với BankAccount.java hiện tại — có khác biệt nào về style (ví dụ: thiếu @Builder.Default cho giá trị mặc định, sai thứ tự annotation) không? Các ManyToOne relationship có dùng FetchType.LAZY giống BankAccount → Customer không?
```

---

## Prompt 3: Áp dụng Strategy Pattern cho logic tính Cashback

```
[VAI TRÒ] Bạn là một Java developer có kinh nghiệm áp dụng Design Pattern trong hệ thống tài chính, ưu tiên code sạch (clean code) và dễ mở rộng, tránh tổ hợp if/else lồng nhau.

[BỐI CẢNH & NHIỆM VỤ] Trong hệ thống CoreBanking (Spring Boot 3.2.4, Java 17), cần tính phần trăm Cashback cho giao dịch thẻ tín dụng dựa trên 2 yếu tố: Hạng thẻ (CardTier: STANDARD, PLATINUM) và Danh mục chi tiêu (SpendingCategory: GROCERY, TRAVEL, OTHER). Logic cụ thể:
- STANDARD + GROCERY → 1%, STANDARD + TRAVEL → 0.5%, STANDARD + OTHER → 0%.
- PLATINUM + GROCERY → 3%, PLATINUM + TRAVEL → 5%, PLATINUM + OTHER → 0%.
Nhiệm vụ: Implement Strategy Pattern với 4 file: interface CashbackStrategy, 2 concrete strategy (StandardCashbackStrategy, PlatinumCashbackStrategy), và CashbackStrategyFactory. Tất cả đặt tại package com.banking.models.services.strategy.

[YÊU CẦU]
- Interface CashbackStrategy: có method calculateCashbackPercent(SpendingCategory category) trả về BigDecimal (ví dụ: 1% trả về new BigDecimal("1")).
- StandardCashbackStrategy: annotate @Component, implement CashbackStrategy, dùng switch expression (Java 17 syntax) trả đúng tỷ lệ cho từng SpendingCategory.
- PlatinumCashbackStrategy: annotate @Component, implement CashbackStrategy, tương tự nhưng với tỷ lệ PLATINUM.
- CashbackStrategyFactory: annotate @Component + @RequiredArgsConstructor, inject 2 concrete strategy qua constructor, method getStrategy(CardTier tier) trả CashbackStrategy tương ứng, dùng switch expression.
- Không dùng if/else. Dùng switch expression (arrow syntax) cho tất cả logic phân nhánh.
- BigDecimal phải dùng constructor từ String (new BigDecimal("0.5")) thay vì từ double để tránh sai số.

[ĐỊNH DẠNG ĐẦU RA] 4 code block Java riêng biệt, mỗi block bắt đầu bằng comment ghi đường dẫn file. Thứ tự: interface → StandardCashbackStrategy → PlatinumCashbackStrategy → CashbackStrategyFactory.

[KIỂM TRA LẠI] Verify rằng switch expression trong mỗi strategy đã cover hết tất cả giá trị của enum SpendingCategory (GROCERY, TRAVEL, OTHER) — thiếu case nào sẽ gây compile error. Tương tự, CashbackStrategyFactory đã cover hết CardTier (STANDARD, PLATINUM) chưa?
```

---

## Prompt 4: Viết PaymentService xử lý logic nghiệp vụ chính

```
[VAI TRÒ] Bạn là một Spring Boot backend developer chuyên viết Service layer cho hệ thống tài chính, có kinh nghiệm xử lý transaction, validation, và exception handling theo best practices.

[BỐI CẢNH & NHIỆM VỤ] Hệ thống CoreBanking cần Service xử lý luồng thanh toán (Payment) cho thẻ tín dụng với tính năng Cashback. Các thành phần đã có sẵn:
- CreditCardRepository.findById(Long id) → Optional<CreditCard>
- TransactionRepository.save(Transaction tx) → Transaction
- CreditCardRepository.save(CreditCard card) → CreditCard
- CashbackStrategyFactory.getStrategy(CardTier tier) → CashbackStrategy
- CashbackStrategy.calculateCashbackPercent(SpendingCategory category) → BigDecimal (trả % dạng BigDecimal, ví dụ: 1% → BigDecimal("1"))
- PaymentRequest: cardId (Long), amount (BigDecimal), category (String)
- PaymentResponse: transactionId (Long), amountCharged (BigDecimal), cashbackAmount (BigDecimal), rewardPointsEarned (BigDecimal), totalRewardPoints (BigDecimal)
- BusinessException(int code, String message) — exception class sẵn có, GlobalExceptionHandler sẽ trả ResponseEntity theo code truyền vào.
- Enum CardStatus: ACTIVE, INACTIVE. Enum SpendingCategory: GROCERY, TRAVEL, OTHER.
Nhiệm vụ: Viết PaymentService (package com.banking.models.services) với method processPayment(PaymentRequest request) → PaymentResponse.

[YÊU CẦU]
- Annotate @Service, @RequiredArgsConstructor. Inject CreditCardRepository, TransactionRepository, CashbackStrategyFactory.
- Method processPayment phải annotate @Transactional.
- Luồng xử lý theo thứ tự:
  1. Parse request.getCategory() thành SpendingCategory bằng valueOf(). Nếu IllegalArgumentException → throw BusinessException(422, "Invalid spending category: " + category).
  2. Tìm CreditCard bằng cardId. Nếu không tìm thấy → throw BusinessException(422, "Credit card not found with id: " + cardId).
  3. Kiểm tra card.getStatus() == CardStatus.INACTIVE → throw BusinessException(422, "Card is inactive. Payment cannot be processed.").
  4. Gọi CashbackStrategyFactory.getStrategy(card.getCardTier()) để lấy strategy.
  5. Gọi strategy.calculateCashbackPercent(category) để lấy %, rồi tính cashbackAmount = amount × % / 100 (dùng BigDecimal.multiply().divide() với RoundingMode.HALF_UP, scale 4).
  6. rewardPointsEarned = cashbackAmount (1 VND = 1 điểm).
  7. Cộng rewardPointsEarned vào card.getRewardPoints(), save card.
  8. Build Transaction entity (amount, category, cashbackAmount, rewardPoints, creditCard), save.
  9. Build PaymentResponse (transactionId, amountCharged, cashbackAmount, rewardPointsEarned, totalRewardPoints từ card).
- Không dùng try-catch bao ngoài toàn bộ method — chỉ try-catch riêng cho valueOf().

[ĐỊNH DẠNG ĐẦU RA] 1 code block Java duy nhất cho file PaymentService.java, bắt đầu bằng comment ghi đường dẫn file.

[KIỂM TRA LẠI] Xem lại luồng — nếu category là "OTHER" (hợp lệ, parse thành công) nhưng cashback = 0%, service có xử lý đúng không (vẫn tạo transaction với cashbackAmount = 0, rewardPoints = 0, vẫn trả response thành công)? Thứ tự validation có đúng: category parse trước, rồi tìm card, rồi check status không?
```

---

## Prompt 5: Viết PaymentController và cấu hình Security

```
[VAI TRÒ] Bạn là một Spring Boot developer chuyên viết REST Controller và cấu hình Spring Security cho hệ thống ngân hàng.

[BỐI CẢNH & NHIỆM VỤ] Hệ thống CoreBanking đã có PaymentService.processPayment(PaymentRequest) → PaymentResponse. Cần tạo PaymentController để expose API và cập nhật SecurityConfig cho phép truy cập public. Các file hiện có:
- AuthController (com.banking.controllers): dùng pattern @RestController, @RequestMapping("/api/v1/auth"), @RequiredArgsConstructor, inject service, trả ResponseEntity<ApiResponse<T>>.
- ApiResponse<T> (com.banking.advice): có static method success(T data, String message) → ApiResponse<T>.
- SecurityConfig (com.banking.security): hiện cho phép /api/v1/auth/** là permitAll, còn lại authenticated.
Nhiệm vụ: Tạo PaymentController với endpoint POST /api/v1/payments và cập nhật SecurityConfig thêm permitAll cho /api/v1/payments/**.

[YÊU CẦU]
- PaymentController: annotate @RestController, @RequestMapping("/api/v1/payments"), @RequiredArgsConstructor. Inject PaymentService. Method processPayment nhận @Valid @RequestBody PaymentRequest, gọi PaymentService.processPayment(), trả ResponseEntity.ok(ApiResponse.success(response, "Payment processed successfully")).
- SecurityConfig: chỉ thêm 1 dòng .requestMatchers("/api/v1/payments/**").permitAll() ngay sau dòng .requestMatchers("/api/v1/auth/**").permitAll(). Không sửa gì khác.
- Tuân thủ đúng pattern code của AuthController hiện tại (cùng style annotation, import, trả response).

[ĐỊNH DẠNG ĐẦU RA] 2 code block Java: PaymentController.java (file mới) và SecurityConfig.java (chỉ hiển thị đoạn code thay đổi dưới dạng diff, dùng ký hiệu + cho dòng thêm).

[KIỂM TRA LẠI] So sánh PaymentController với AuthController — có dùng cùng cách wrap response (ApiResponse.success()) không? SecurityConfig sau khi thêm dòng mới, thứ tự matcher có hợp lý không (specific paths trước anyRequest)?
```

---

## Prompt 6: Kiểm tra build và sửa lỗi

```
[VAI TRÒ] Bạn là một Java developer có kinh nghiệm debug lỗi biên dịch trong dự án Spring Boot dùng Gradle.

[BỐI CẢNH & NHIỆM VỤ] Dự án CoreBanking (Spring Boot 3.2.4, Java 17, Gradle, Lombok) vừa được thêm nhiều file mới (3 enum, 2 entity, 2 repository, 4 strategy class, 1 service, 1 controller, 1 thay đổi SecurityConfig). Cần chạy build để phát hiện lỗi biên dịch, sau đó sửa nếu có.

[YÊU CẦU]
- Chạy lệnh: ./gradlew build -x test (bỏ qua test, chỉ compile).
- Nếu BUILD SUCCESSFUL: xác nhận không cần sửa gì.
- Nếu BUILD FAILED: đọc kỹ log lỗi, xác định file và dòng gây lỗi, sửa đúng lỗi đó mà không thay đổi logic nghiệp vụ hay phá vỡ cấu trúc code hiện tại. Chỉ sửa lỗi compile, không refactor hay "cải tiến" gì thêm.

[ĐỊNH DẠNG ĐẦU RA] Nếu thành công: ghi "BUILD SUCCESSFUL — Không cần sửa gì." Nếu có lỗi: liệt kê từng lỗi dưới dạng bảng (File, Dòng, Lỗi, Cách sửa), sau đó kèm code block chứa đoạn code đã sửa cho mỗi file.

[KIỂM TRA LẠI] Sau khi sửa, chạy lại ./gradlew build -x test lần nữa — có còn lỗi nào không? Nếu vẫn fail, lặp lại quy trình.
```
