# Gemini multi-key pool và Neo4j-first chat

## Mục tiêu

- Phân tán tải và tăng độ tin cậy khi gọi Gemini bằng **pool nhiều API key** (rotation, circuit breaker theo key, backoff).
- Ưu tiên **trả lời từ đồ thị Neo4j** khi đủ ngữ cảnh; chỉ gọi Gemini khi điểm tin cậy thấp hoặc thiếu dữ liệu graph.

## Cấu hình

| Biến / property | Ý nghĩa |
|-----------------|--------|
| `GEMINI_API_KEYS` | Nhiều key, phân tách bằng dấu phẩy (ưu tiên). Map vào `gemini.api.keys`. |
| `GEMINI_API_KEY` | Một key đơn (tương thích ngược). |
| `GEMINI_POOL_STRATEGY` | `ROUND_ROBIN` (mặc định), `LEAST_RECENTLY_USED`, `LEAST_USED`, `WEIGHTED_LEAST_USED`. |
| `GEMINI_POOL_MAX_ATTEMPTS` | Tối đa lần thử cho một request (xuyên suốt các key). |
| `APP_AI_NEO4J_FIRST_ENABLED` | Bật/tắt luồng ưu tiên Neo4j (`true` mặc định). |
| `APP_AI_NEO4J_FIRST_MIN_CONFIDENCE` | Ngưỡng [0–1]; đạt thì trả lời graph-first, không gọi Gemini cho phản hồi chính. |

**Không** đặt API key thật trong `application.properties` trong môi trường production; chỉ inject qua biến môi trường hoặc secret store.

## Kiến trúc code

- `GeminiBootstrap`: gom key từ `gemini.api.keys` + `gemini.api.key`.
- `GeminiApiKeyPool`: chọn slot, metric, cooldown sau 429, circuit khi lỗi lặp.
- `GeminiApiClient`: HTTP WebClient, retry + exponential backoff, gọi lại key khác khi 429 / lỗi.
- `GeminiService` / `GeminiAIService`: facade mỏng gọi `GeminiApiClient`.
- `Neo4jFirstChatPolicy`: tính điểm tin cậy cho graph-first.
- `AiAssistantService`: nếu đủ tin cậy → `buildNeo4jPrimaryReply`; ngược lại → Gemini.

## Giám sát

- Admin: `GET /api/v1/admin/ai/gemini-key-metrics` (role ADMIN) — key đã mask, số lần dùng, 429, cooldown/circuit còn lại.

## Tuân thủ và vận hành

- Chỉ dùng các API key được Google cấp phép cho tổ chức của bạn (nhiều project/quota hợp lệ). Việc vượt quota hoặc lạm dụng có thể vi phạm điều khoản dịch vụ.
- Benchmark 10k+ concurrent requests: chạy load test riêng (Gatling/k6) với stack thực (Neo4j, Redis, pool key); không cam kết số trong repo.

## Zero-downtime

- Deploy bản mới với env `GEMINI_API_KEYS` đã set trước; rollout rolling/restart graceful — không cần migration DB cho tính năng này.
