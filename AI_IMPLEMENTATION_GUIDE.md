# 🚀 AI Chat Box - Backend Implementation Guide
## Tích hợp Explainable AI, Feedback Loop & Substitution Logic

**Ngày cập nhật:** 09/05/2026  
**Mục tiêu:** Đưa hệ thống từ 89.6% → 95%+ Completion

---

## 📋 Tóm Tắt Công Việc

| Tác vụ | Mô tả | Độ khó | Ưu tiên |
| :--- | :--- | :---: | :---: |
| **1. Add Explanations** | Thêm giải thích cho mỗi sản phẩm được đề xuất | ⭐⭐ | 🔴 Cao |
| **2. Trust Score** | Hiển thị độ tin cậy của AI (0-100%) | ⭐ | 🔴 Cao |
| **3. Feedback Persistence** | Lưu trữ feedback người dùng vào database | ⭐⭐ | 🔴 Cao |
| **4. Substitution Logic** | Tự động gợi ý sản phẩm thay thế dựa trên Neo4j | ⭐⭐⭐ | 🟡 Trung |
| **5. Rate Limiting** | Giới hạn số lượt chat/phút để chống spam | ⭐⭐ | 🟢 Thấp |

---

## 🔧 Chi Tiết Từng Tác Vụ

### 1️⃣ Add Explanations to Recommendations

**Vị trí:** `AiAssistantService.processChat()`

**Code cần thêm:**
```java
// Sau khi lấy danh sách recommended products từ Neo4j
Map<Long, String> explanations = new HashMap<>();

for (Long productId : includedProductIds) {
    String reason = generateExplanationForProduct(userId, productId, profileOpt.orElse(null), userMessage);
    explanations.put(productId, reason);
}

// Thêm vào response
response.setExplanations(explanations);
```

**Hàm `generateExplanationForProduct`:**
```java
private String generateExplanationForProduct(Long userId, Long productId, 
        UserNutritionProfile profile, String userMessage) {
    StringBuilder reason = new StringBuilder();
    
    // Lấy Product từ database
    Product product = productRepository.findById(productId).orElse(null);
    if (product == null) return "Không có thông tin";
    
    // Kiểm tra các điều kiện
    if (profile != null) {
        // 1. Kiểm tra dị ứng
        if (profile.getAllergies() != null && profile.getAllergies().contains(product.getName())) {
            return "❌ Sản phẩm này chứa thành phần bạn dị ứng - nên tránh";
        }
        
        // 2. Kiểm tra mục tiêu sức khỏe
        if (profile.getHealthGoals() != null) {
            if (profile.getHealthGoals().contains("Giảm cân") && product.getCalories() < 100) {
                reason.append("✓ Lượng calo thấp, phù hợp với mục tiêu giảm cân\n");
            }
            if (profile.getHealthGoals().contains("Tăng cơ") && product.getProtein() > 10) {
                reason.append("✓ Giàu protein, hỗ trợ phát triển cơ bắp\n");
            }
        }
        
        // 3. Kiểm tra chế độ ăn
        if (profile.getDietaryPreference() != null) {
            if (profile.getDietaryPreference().contains("Chay") && product.isVegetarian()) {
                reason.append("✓ Phù hợp với chế độ ăn chay của bạn\n");
            }
        }
    }
    
    // 4. Kiểm tra độ phổ biến / rating
    if (product.getAverageRating() >= 4.5) {
        reason.append("⭐ Được đánh giá cao (").append(product.getAverageRating()).append(" sao)\n");
    }
    
    // 5. Kiểm tra khuyến mãi / giảm giá
    if (product.getDiscountPercentage() != null && product.getDiscountPercentage() > 0) {
        reason.append("💰 Đang được giảm giá ").append(product.getDiscountPercentage()).append("%\n");
    }
    
    return reason.length() > 0 ? reason.toString().trim() : "💡 Sản phẩm phù hợp với sở thích của bạn";
}
```

---

### 2️⃣ Calculate & Return Trust Score

**Vị trí:** `AiAssistantService.processChat()`

**Code cần thêm:**
```java
// Tính toán trust score (0-100)
int trustScore = calculateTrustScore(userId, profileOpt.orElse(null), 
        recommendedNodes, userMessage);

// Thêm vào response
response.setTrustScore(trustScore);
```

**Hàm `calculateTrustScore`:**
```java
private int calculateTrustScore(Long userId, UserNutritionProfile profile,
        List<ProductNode> recommendedNodes, String userMessage) {
    int score = 50; // Base score
    
    // 1. User profile completeness (+20%)
    if (profile != null) {
        int completeness = 0;
        if (profile.getBmi() != null) completeness += 2;
        if (profile.getHealthGoals() != null) completeness += 2;
        if (profile.getDietaryPreference() != null) completeness += 2;
        if (profile.getAllergies() != null) completeness += 2;
        score += completeness;
    }
    
    // 2. Number of recommendations (+10% max)
    score += Math.min(10, recommendedNodes.size() / 2);
    
    // 3. Message clarity (heuristic - nếu message dài & rõ ràng, score +15%)
    if (userMessage.length() > 30) {
        score += 15;
    }
    
    // Clamp to 0-100
    return Math.max(0, Math.min(100, score));
}
```

---

### 3️⃣ Add Feedback Persistence

**Tạo Entity mới:** `src/main/java/com/smartgrocery/backend/entity/ChatMessageFeedback.java`

```java
package com.smartgrocery.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message_feedbacks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackType feedbackType; // HELPFUL, NOT_HELPFUL, CONFUSING

    @Column(length = 500)
    private String reason; // User explanation

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum FeedbackType {
        HELPFUL, NOT_HELPFUL, CONFUSING
    }
}
```

**Tạo Repository:** `src/main/java/com/smartgrocery/backend/repository/ChatMessageFeedbackRepository.java`

```java
package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.ChatMessageFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageFeedbackRepository extends JpaRepository<ChatMessageFeedback, Long> {
    ChatMessageFeedback findByUser_IdAndChatMessage_Id(Long userId, Long messageId);
}
```

**Cập nhật `AiAssistantService`:**

```java
@Autowired
private ChatMessageFeedbackRepository feedbackRepository;

@Transactional
public ChatFeedbackResponse submitFeedback(Long userId, ChatFeedbackRequest request) {
    // Tìm ChatMessage dựa trên messageId (cần thêm UUID hoặc external ID vào ChatMessage)
    // Hoặc có thể lưu mapping messageId -> chatMessageId trong Redis cache
    
    User user = userRepository.findById(userId).orElseThrow();
    // Giả sử ta tìm được ChatMessage
    ChatMessage message = findChatMessageByExternalId(request.getMessageId());
    
    ChatMessageFeedback feedback = ChatMessageFeedback.builder()
            .user(user)
            .chatMessage(message)
            .feedbackType(ChatMessageFeedback.FeedbackType.valueOf(request.getFeedbackType().toString()))
            .reason(request.getReason())
            .build();
    
    ChatMessageFeedback saved = feedbackRepository.save(feedback);
    
    return ChatFeedbackResponse.builder()
            .feedbackId(saved.getId())
            .messageId(request.getMessageId())
            .feedbackType(request.getFeedbackType().toString())
            .reason(request.getReason())
            .createdAt(saved.getCreatedAt())
            .build();
}
```

---

### 4️⃣ Substitution Logic (Advanced)

**Cập nhật Neo4j Query:**

```java
// Trong ProductNodeRepository
@Query("""
    MATCH (user:User {userId: $userId})-[rel:AVOIDS_CATEGORY]->(cat:Category),
          (product:Product)-[:BELONGS_TO]->(cat),
          (substitute:Product)-[:SIMILAR_TO]-(product)
    WHERE substitute.isActive = true
    RETURN substitute
    LIMIT 10
""")
List<ProductNode> findSubstitutionProducts(Long userId);
```

**Sử dụng trong Service:**

```java
@Transactional
public AiChatResponse processChat(Long userId, String userMessage, Long sessionId) {
    // ... existing code ...
    
    List<AiChatResponse.ProposedItem> proposedItems = new ArrayList<>();
    
    for (Long productId : includedProductIds) {
        // Check if current item has substitutes
        List<ProductNode> substitutes = productNodeRepository
            .findSubstitutionProducts(userId);
        
        Long substitutionFor = null;
        if (!substitutes.isEmpty()) {
            substitutionFor = substitutes.get(0).getProductId();
        }
        
        proposedItems.add(AiChatResponse.ProposedItem.builder()
                .productId(productId)
                .quantity(1)
                .reason(explanations.get(productId))
                .substitutionFor(substitutionFor)
                .build());
    }
    
    response.setProposedItems(proposedItems);
    return response;
}
```

---

### 5️⃣ Rate Limiting (Optional Priority)

**Thêm Redis Dependency:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Tạo Annotation:**
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String key();
    int permitsPerMinute() default 10;
}
```

**Tạo Interceptor:**
```java
@Component
public class RateLimitInterceptor {
    @Autowired
    private RedisTemplate<String, Integer> redisTemplate;
    
    public void checkRateLimit(String key, int permitsPerMinute) {
        Long current = redisTemplate.opsForValue().increment(key);
        if (current == 1) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        if (current > permitsPerMinute) {
            throw new TooManyRequestsException("Rate limit exceeded");
        }
    }
}
```

---

## 📝 Database Migrations

**Tạo Migration File:**
```sql
-- V1.0.1__add_explanations_and_feedback.sql

ALTER TABLE ai_chat_response ADD COLUMN explanations JSON COMMENT 'Map of productId -> explanation';
ALTER TABLE ai_chat_response ADD COLUMN trust_score INTEGER DEFAULT 50 COMMENT '0-100 confidence score';

ALTER TABLE ai_proposed_item ADD COLUMN reason VARCHAR(500) COMMENT 'Why this item was suggested';
ALTER TABLE ai_proposed_item ADD COLUMN substitution_for BIGINT REFERENCES product(id) COMMENT 'If this is a replacement';

CREATE TABLE chat_message_feedbacks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL REFERENCES users(id),
    chat_message_id BIGINT NOT NULL REFERENCES chat_messages(id),
    feedback_type ENUM('HELPFUL', 'NOT_HELPFUL', 'CONFUSING') NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_feedback (user_id, chat_message_id)
);

CREATE INDEX idx_feedback_user ON chat_message_feedbacks(user_id);
CREATE INDEX idx_feedback_message ON chat_message_feedbacks(chat_message_id);
```

---

## 🧪 Testing Checklist

- [ ] Verify explanations are generated for all product types
- [ ] Test trust score calculation with various user profiles
- [ ] Verify feedback is persisted and retrieved correctly
- [ ] Test substitution logic returns valid alternatives
- [ ] Load test rate limiting under high concurrency
- [ ] Verify API responses match frontend type definitions

---

## 🚀 Deployment Steps

1. **Update Dependencies** (if needed for Redis)
2. **Run Database Migrations** using Flyway/Liquibase
3. **Deploy Backend (Backend)**:
   ```bash
   mvn clean package
   java -jar target/smartgrocery-backend.jar
   ```
4. **Verify Endpoints** using Swagger UI: `http://localhost:8080/swagger-ui.html`
5. **Test Frontend** integration with updated API
6. **Monitor Logs** for any exceptions or rate limit triggers

---

## 📊 Success Metrics

| Metric | Target | Current |
| :--- | :--- | :--- |
| **Completion %** | 95%+ | 89.6% |
| **Explanation Accuracy** | 90% | TBD |
| **Feedback Collection Rate** | 20%+ | 0% |
| **Substitution Success** | 70%+ | N/A |
| **API Latency** | <500ms | TBD |

---

## 📞 Support

For questions, contact the backend team or refer to the full implementation guide at:  
[GitHub Wiki: AI Assistant Implementation](https://github.com/smartgrocery/docs)
