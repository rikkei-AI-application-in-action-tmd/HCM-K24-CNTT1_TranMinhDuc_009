# Prompt History — Cashback/Rewards System

This document records all the prompts used with the AI to build the Cashback/Rewards feature for the CoreBanking system. Each prompt follows the 5-part format (Role, Context & Task, Requirements, Output Format, Verification).

---

## Prompt 1: Business Analysis & SRS Writing

```
[ROLE] You are a Business Analyst specializing in Core Banking systems, experienced in writing Software Requirements Specification (SRS) documents for credit card features and loyalty programs.

[CONTEXT & TASK] The bank is launching a "Cashback/Rewards" system for Credit Cards. The current CoreBanking system already has the entities: Customer, BankAccount (with AccountType including CHECKING, SAVINGS, CREDIT). The business requirements are as follows:
- Credit cards are divided into 2 tiers: STANDARD and PLATINUM.
- When a customer swipes the card (executing the Payment API), the system automatically calculates the cashback based on the Card Tier and Spending Category.
- Mandatory logic:
  + "Grocery" category: STANDARD gets 1% cashback, PLATINUM gets 3% cashback.
  + "Travel" category: STANDARD gets 0.5% cashback, PLATINUM gets 5% cashback.
  + Other categories: No cashback.
- The Payment API must return the total deducted amount and the reward points earned in that transaction.
Task: Write a complete SRS document for this feature, including the entity design for CreditCard (containing the tier as Enum and card status) and Transaction (containing the spending category), build a Decision Table for the cashback percentage logic combining Card Tier and Category, describe the Payment API (endpoint, request/response format), and list the exception handling cases (locked card, invalid category).

[REQUIREMENTS]
- The Decision Table must have exactly 6 combinations (2 tiers × 3 categories) with an example column illustrating specific amounts.
- The Entity design must specify the data type for each field (use BigDecimal for currency, Enum for card tier/status/category).
- The CreditCard entity must have a rewardPoints (BigDecimal) field to accumulate reward points after each transaction.
- Clearly describe the relationships between entities (CreditCard ManyToOne Customer, Transaction ManyToOne CreditCard).
- Payment API: POST endpoint /api/v1/payments, request receives cardId + amount + category, response returns transactionId + amountCharged + cashbackAmount + rewardPointsEarned + totalRewardPoints.
- Exception handling: card not found → 422, card INACTIVE → 422, invalid category → 422. Clearly describe the HTTP status code and message for each case.
- Propose an appropriate Design Pattern (Strategy Pattern) to decouple the cashback calculation logic by card tier, avoiding long if/else blocks.

[OUTPUT FORMAT] A Markdown (.md) file with a clear structure and numbered sections: Introduction, Entity Design (table describing each field), Decision Table (6-row table), Payment API (endpoint + sample JSON request/response), Design Pattern, Exception Handling (table listing conditions, HTTP status, message).

[VERIFICATION] Re-read the Decision Table — are there exactly 6 combinations? Do the percentages match the stated business requirements? Are there any missing fields in the entities compared to the requirements (e.g., CreditCard missing status, Transaction missing cashbackAmount)?
```

---

## Prompt 2: Entity and Enum Design for CreditCard and Transaction

```
[ROLE] You are a Spring Boot developer specializing in designing JPA Entities for Core Banking systems, familiar with Lombok, Jakarta Persistence, and best practices for financial data models.

[CONTEXT & TASK] The current CoreBanking project (Spring Boot 3.2.4, Java 17, Gradle, MySQL, Lombok) already has Customer and BankAccount entities. The current package structure is:
- Entity: com.banking.models.entities (BankAccount.java, Customer.java)
- Enum: com.banking.models.constant (CustomerStatus.java)
- Repository: com.banking.models.repositories (BankAccountRepository, CustomerRepository)
Current pattern: Entities use @Builder, @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor; use BigDecimal for currency with precision=19, scale=4; @PrePersist for createdAt; enums use @Enumerated(EnumType.STRING).
Task: Create 3 new enums (CardTier, CardStatus, SpendingCategory) and 2 new entities (CreditCard, Transaction) along with their 2 corresponding repositories, strictly adhering to the current code pattern.

[REQUIREMENTS]
- CardTier Enum: STANDARD, PLATINUM. Placed in com.banking.models.constant.
- CardStatus Enum: ACTIVE, INACTIVE. Placed in com.banking.models.constant.
- SpendingCategory Enum: GROCERY, TRAVEL, OTHER. Placed in com.banking.models.constant.
- CreditCard Entity (table: credit_cards): id (Long PK auto), cardNumber (String unique 16 chars), cardTier (Enum CardTier), status (Enum CardStatus, default ACTIVE), rewardPoints (BigDecimal default ZERO, precision 19 scale 4), customer (ManyToOne LAZY → Customer), createdAt (LocalDateTime, @PrePersist).
- Transaction Entity (table: transactions): id (Long PK auto), amount (BigDecimal, precision 19 scale 4), category (Enum SpendingCategory), cashbackAmount (BigDecimal, precision 19 scale 4), rewardPoints (BigDecimal, precision 19 scale 4), creditCard (ManyToOne LAZY → CreditCard), createdAt (LocalDateTime, @PrePersist).
- Both entities must use @Builder, @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor just like the existing BankAccount.
- Repository: CreditCardRepository extends JpaRepository<CreditCard, Long>, TransactionRepository extends JpaRepository<Transaction, Long>. Placed in com.banking.models.repositories.
- Do not add unnecessary imports, do not add methods outside the requirements.

[OUTPUT FORMAT] Each Java file should be in a separate code block, starting with a comment specifying the exact file path (e.g., // src/main/java/com/banking/models/constant/CardTier.java). A total of 7 code blocks for 7 files: 3 enums + 2 entities + 2 repositories.

[VERIFICATION] Compare the annotations and field declarations of CreditCard/Transaction with the existing BankAccount.java — are there any style differences (e.g., missing @Builder.Default for default values, wrong annotation order)? Do the ManyToOne relationships use FetchType.LAZY just like BankAccount → Customer?
```

---

## Prompt 3: Applying Strategy Pattern for Cashback Logic

```
[ROLE] You are a Java developer experienced in applying Design Patterns in financial systems, prioritizing clean code and extensibility, avoiding nested if/else blocks.

[CONTEXT & TASK] In the CoreBanking system (Spring Boot 3.2.4, Java 17), the Cashback percentage for credit card transactions needs to be calculated based on 2 factors: Card Tier (CardTier: STANDARD, PLATINUM) and Spending Category (SpendingCategory: GROCERY, TRAVEL, OTHER). Specific logic:
- STANDARD + GROCERY → 1%, STANDARD + TRAVEL → 0.5%, STANDARD + OTHER → 0%.
- PLATINUM + GROCERY → 3%, PLATINUM + TRAVEL → 5%, PLATINUM + OTHER → 0%.
Task: Implement the Strategy Pattern with 4 files: CashbackStrategy interface, 2 concrete strategies (StandardCashbackStrategy, PlatinumCashbackStrategy), and CashbackStrategyFactory. All placed in the com.banking.models.services.strategy package.

[REQUIREMENTS]
- CashbackStrategy Interface: has method calculateCashbackPercent(SpendingCategory category) returning BigDecimal (e.g., 1% returns new BigDecimal("1")).
- StandardCashbackStrategy: annotate with @Component, implement CashbackStrategy, use a switch expression (Java 17 syntax) to return the correct percentage for each SpendingCategory.
- PlatinumCashbackStrategy: annotate with @Component, implement CashbackStrategy, similar but with PLATINUM percentages.
- CashbackStrategyFactory: annotate with @Component + @RequiredArgsConstructor, inject the 2 concrete strategies via constructor, method getStrategy(CardTier tier) returns the corresponding CashbackStrategy, using a switch expression.
- Do not use if/else. Use switch expressions (arrow syntax) for all branching logic.
- BigDecimal must use the String constructor (new BigDecimal("0.5")) instead of double to avoid precision errors.

[OUTPUT FORMAT] 4 separate Java code blocks, each block starting with a comment specifying the file path. Order: interface → StandardCashbackStrategy → PlatinumCashbackStrategy → CashbackStrategyFactory.

[VERIFICATION] Verify that the switch expression in each strategy covers all values of the SpendingCategory enum (GROCERY, TRAVEL, OTHER) — a missing case will cause a compile error. Similarly, does the CashbackStrategyFactory cover all CardTiers (STANDARD, PLATINUM)?
```

---

## Prompt 4: Writing PaymentService for Core Business Logic

```
[ROLE] You are a Spring Boot backend developer specializing in writing Service layers for financial systems, experienced with transaction processing, validation, and exception handling best practices.

[CONTEXT & TASK] The CoreBanking system needs a Service to handle the payment flow for credit cards with the Cashback feature. Available components:
- CreditCardRepository.findById(Long id) → Optional<CreditCard>
- TransactionRepository.save(Transaction tx) → Transaction
- CreditCardRepository.save(CreditCard card) → CreditCard
- CashbackStrategyFactory.getStrategy(CardTier tier) → CashbackStrategy
- CashbackStrategy.calculateCashbackPercent(SpendingCategory category) → BigDecimal (returns % as BigDecimal, e.g., 1% → BigDecimal("1"))
- PaymentRequest: cardId (Long), amount (BigDecimal), category (String)
- PaymentResponse: transactionId (Long), amountCharged (BigDecimal), cashbackAmount (BigDecimal), rewardPointsEarned (BigDecimal), totalRewardPoints (BigDecimal)
- BusinessException(int code, String message) — existing exception class, GlobalExceptionHandler will return ResponseEntity based on the passed code.
- Enum CardStatus: ACTIVE, INACTIVE. Enum SpendingCategory: GROCERY, TRAVEL, OTHER.
Task: Write PaymentService (in package com.banking.models.services) with method processPayment(PaymentRequest request) → PaymentResponse.

[REQUIREMENTS]
- Annotate with @Service, @RequiredArgsConstructor. Inject CreditCardRepository, TransactionRepository, CashbackStrategyFactory.
- The processPayment method must be annotated with @Transactional.
- Execution flow in order:
  1. Parse request.getCategory() to SpendingCategory using valueOf(). If IllegalArgumentException → throw BusinessException(422, "Invalid spending category: " + category).
  2. Find CreditCard by cardId. If not found → throw BusinessException(422, "Credit card not found with id: " + cardId).
  3. Check card.getStatus() == CardStatus.INACTIVE → throw BusinessException(422, "Card is inactive. Payment cannot be processed.").
  4. Call CashbackStrategyFactory.getStrategy(card.getCardTier()) to get the strategy.
  5. Call strategy.calculateCashbackPercent(category) to get the %, then calculate cashbackAmount = amount × % / 100 (using BigDecimal.multiply().divide() with RoundingMode.HALF_UP, scale 4).
  6. rewardPointsEarned = cashbackAmount (1 VND = 1 point).
  7. Add rewardPointsEarned to card.getRewardPoints(), save card.
  8. Build Transaction entity (amount, category, cashbackAmount, rewardPoints, creditCard), save.
  9. Build PaymentResponse (transactionId, amountCharged, cashbackAmount, rewardPointsEarned, totalRewardPoints from card).
- Do not use a try-catch wrapping the entire method — only try-catch specifically for valueOf().

[OUTPUT FORMAT] 1 single Java code block for the PaymentService.java file, starting with a comment specifying the file path.

[VERIFICATION] Review the flow — if the category is "OTHER" (valid, parsed successfully) but the cashback is 0%, does the service handle it correctly (still creates a transaction with cashbackAmount = 0, rewardPoints = 0, still returns a successful response)? Is the validation order correct: parse category first, then find card, then check status?
```

---

## Prompt 5: Writing PaymentController and Security Configuration

```
[ROLE] You are a Spring Boot developer specializing in writing REST Controllers and configuring Spring Security for banking systems.

[CONTEXT & TASK] The CoreBanking system already has PaymentService.processPayment(PaymentRequest) → PaymentResponse. We need to create PaymentController to expose the API and update SecurityConfig to allow public access. Existing files:
- AuthController (com.banking.controllers): uses the @RestController, @RequestMapping("/api/v1/auth"), @RequiredArgsConstructor pattern, injects service, returns ResponseEntity<ApiResponse<T>>.
- ApiResponse<T> (com.banking.advice): has static method success(T data, String message) → ApiResponse<T>.
- SecurityConfig (com.banking.security): currently allows /api/v1/auth/** as permitAll, everything else is authenticated.
Task: Create PaymentController with a POST /api/v1/payments endpoint and update SecurityConfig to add permitAll for /api/v1/payments/**.

[REQUIREMENTS]
- PaymentController: annotate with @RestController, @RequestMapping("/api/v1/payments"), @RequiredArgsConstructor. Inject PaymentService. The processPayment method receives @Valid @RequestBody PaymentRequest, calls PaymentService.processPayment(), returns ResponseEntity.ok(ApiResponse.success(response, "Payment processed successfully")).
- SecurityConfig: just add 1 line .requestMatchers("/api/v1/payments/**").permitAll() right after the line .requestMatchers("/api/v1/auth/**").permitAll(). Do not modify anything else.
- Strictly adhere to the code pattern of the existing AuthController (same style of annotations, imports, response wrapping).

[OUTPUT FORMAT] 2 Java code blocks: PaymentController.java (new file) and SecurityConfig.java (only show the modified code snippet as a diff, use the + symbol for added lines).

[VERIFICATION] Compare PaymentController with AuthController — does it use the same response wrapping method (ApiResponse.success())? In SecurityConfig, after adding the new line, is the matcher order logical (specific paths before anyRequest)?
```

---

## Prompt 6: Build Check and Bug Fixing

```
[ROLE] You are a Java developer experienced in debugging compilation errors in Spring Boot projects using Gradle.

[CONTEXT & TASK] The CoreBanking project (Spring Boot 3.2.4, Java 17, Gradle, Lombok) just had several new files added (3 enums, 2 entities, 2 repositories, 4 strategy classes, 1 service, 1 controller, 1 change in SecurityConfig). A build needs to be run to detect compilation errors, then fix them if any exist.

[REQUIREMENTS]
- Run command: ./gradlew build -x test (skip tests, compile only).
- If BUILD SUCCESSFUL: confirm that no fixes are needed.
- If BUILD FAILED: carefully read the error logs, identify the file and line causing the error, fix exactly that error without changing the business logic or breaking the existing code structure. Only fix compile errors, do not refactor or "improve" anything else.

[OUTPUT FORMAT] If successful: write "BUILD SUCCESSFUL — No fixes needed." If there are errors: list each error in a table (File, Line, Error, Fix), then provide a code block containing the fixed code snippet for each file.

[VERIFICATION] After fixing, run ./gradlew build -x test again — are there any remaining errors? If it still fails, repeat the process.
```
