# 🛒 SmartGrocery Backend Service

Chào mừng bạn đến với **SmartGrocery Backend** – trái tim công nghệ điều phối toàn bộ hệ thống bán lẻ thực phẩm thông minh. Hệ thống được phát triển trên nền tảng **Spring Boot (Java 17)** kết hợp hệ thống AI tiên tiến và hạ tầng cơ sở dữ liệu tối ưu hóa vượt trội.

---

## 🏗️ 1. Kiến trúc hệ thống & Công nghệ cốt lõi

* **Hệ khung (Framework):** Spring Boot 3.x, Spring Security, Spring Mail
* **Cơ sở dữ liệu (Database):** Supabase PostgreSQL (Flyway migration quản lý phiên bản tự động)
* **Trí tuệ nhân tạo (AI Engine):** Tích hợp qua OpenRouter (DeepSeek V4 Flash & Gemini 2.0 Flash)
* **Bảo mật:** JWT (JSON Web Token), mã hóa mật khẩu BCrypt, cơ chế kiểm tra vân tay thiết bị (Device Fingerprint) ngăn chặn chiếm quyền đăng nhập.
* **Tối ưu hóa DB:** Sử dụng chỉ mục chuyên sâu **PostgreSQL Trigram (pg_trgm)** và **GIN Indexes** cho tìm kiếm không dấu tốc độ cao.

---

## 🌟 2. Các phân hệ nghiệp vụ chính

### 👤 Customer Module (Khách hàng)
* **Xác thực:** Đăng ký, đăng nhập bảo mật hai lớp qua mã OTP gửi tới Email thực tế. Chức năng khôi phục mật khẩu an toàn.
* **Hồ sơ:** Chỉnh sửa thông tin cá nhân, cập nhật Avatar cực nhanh nhờ cơ chế tải lên Supabase Storage trực tiếp.
* **Mua sắm:** Xem thông tin sản phẩm, quản lý danh sách yêu thích (Wishlist), thêm hàng vào giỏ và thanh toán.

### 🤖 AI Shopping Assistant (Trợ lý mua sắm AI)
* **Gợi ý món ăn:** Tự động đề xuất thực đơn dựa trên Hồ sơ sức khỏe cá nhân (Allergies - Dị ứng, Chế độ ăn uống, Chỉ số BMI, Mục tiêu dinh dưỡng).
* **Bóc tách danh sách:** Tự động tạo danh sách nguyên liệu và khớp chính xác các biến thể sản phẩm (SKUs) đang bán tại cửa hàng.
* **Khuyến mại & Voucher:** AI tự động gợi ý các sản phẩm giảm giá và trao tặng Voucher khi khách hàng mua hàng theo thực đơn AI.

### 👥 Staff Attendance & Order Fullfillment (Nhân viên)
* **Chấm công:** Đăng ký ca làm, Check-in/Check-out dựa trên thời gian thực có ràng buộc khoảng đệm an toàn.
* **Xử lý đơn hàng:** Quy trình 3 bước chặt chẽ (Tiếp nhận -> Đóng gói -> Giao hàng) có xác minh số lượng sản phẩm.
* **Hiệu suất & Lương:** Thống kê KPI hoạt động theo ngày/tuần/tháng và tự động tính bảng lương hàng tháng dựa trên hiệu suất đóng gói đơn hàng.

---

## 🛠️ 3. Hướng dẫn thiết lập môi trường phát triển

### Bước 1: Chuẩn bị môi trường
* Đảm bảo máy tính của bạn đã cài đặt **Java 17 (JDK)** và **Maven 3.8+**.

### Bước 2: Tạo tệp cấu hình `.env`
Tạo tệp `.env` tại thư mục gốc của dự án backend và điền các khóa cấu hình sau:

```properties
# 1. Cấu hình Supabase PostgreSQL
SUPABASE_DB_URL=jdbc:postgresql://<HOST_DATABASE>:<PORT>/postgres?sslmode=require&ssl=true
SUPABASE_DB_USERNAME=<USERNAME_DB>
SUPABASE_DB_PASSWORD=<MẬT_KHẨU_DB>
SUPABASE_URL=https://<SUPABASE_PROJECT_ID>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<SECRET_SERVICE_ROLE_KEY>

# 2. Cấu hình JWT Token Security
JWT_SECRET_KEY=94e77353f81580f089856f4d547f4859fcedf697486e24564850785748574857
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

# 3. Cấu hình OpenRouter AI Keys
OPENROUTER_API_KEYS=<MÃ_API_KEY_AI_CỦA_BẠN>
OPENROUTER_MODEL=deepseek/deepseek-v4-flash
OPENROUTER_PASS1_MODEL=google/gemini-2.0-flash-001
DEEPSEEK_API_KEY=<MÃ_API_KEY_DEEPSEEK_CỦA_BẠN>

# 4. Cấu hình gửi Email OTP (Gmail SMTP)
# Hướng dẫn tạo mật khẩu ứng dụng Gmail: https://myaccount.google.com/apppasswords
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-gmail-app-password
MAIL_FROM=your-email@gmail.com

# 5. Khóa bảo mật OTP Server
OTP_SERVER_SECRET=smartgrocery-super-secret-otp-key-2026
```

---

## 🚀 4. Khởi chạy dự án

1. **Biên dịch và tải thư viện:**
   ```bash
   mvn clean compile
   ```
2. **Khởi chạy Server:**
   ```bash
   mvn spring-boot:run
   ```
   * *Ứng dụng sẽ chạy tại:* `http://localhost:8080`
   * *Swagger API Documentation:* `http://localhost:8080/swagger-ui.html`

---

## 📁 5. Cấu trúc thư mục cốt lõi
```text
SmartGrocery-BackEnd/
├── src/main/java/com/smartgrocery/backend/
│   ├── controller/      # API Rest Controllers (Ai, Auth, Profile, Files, Orders, v.v.)
│   ├── entity/          # JPA Entities (User, Order, Product, Attendance, v.v.)
│   ├── dto/             # Data Transfer Objects (Yêu cầu/Phản hồi API)
│   ├── repository/      # JPA repositories giao tiếp Database
│   ├── service/         # Business Logic xử lý nghiệp vụ chính
│   └── security/        # Cấu hình Spring Security & Filter bảo mật
└── src/main/resources/
    ├── application.properties # File cấu hình Spring chính
    └── db/migration/          # Các tệp Flyway SQL migration di chuyển DB tự động
```
