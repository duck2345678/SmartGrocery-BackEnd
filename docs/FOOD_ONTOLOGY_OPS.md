# Tài liệu Hướng dẫn Vận hành Ontology Thực phẩm (Food Ontology Ops)

## 1. Giới thiệu
Hệ thống SmartGrocery AI Assistant sử dụng Neo4j để lưu trữ Ontology Thực phẩm. Nhờ cơ chế **In-Memory Keyword Normalization (Aho-Corasick)**, AI có thể tự động nhận diện chính xác hàng ngàn tên sản phẩm từ tiếng Việt có dấu, không dấu, đến teencode, và tuyệt đối không bị nhận diện nhầm/chồng chéo giữa các sản phẩm (ví dụ "bông cải xanh" và "cá nục bông").

Tài liệu này hướng dẫn quản trị viên (Admin) cách thêm các từ lóng, tên gọi khác, hoặc từ đồng nghĩa mới vào hệ thống mà **không cần khởi động lại Server hay Deploy lại Code**.

## 2. Cách thêm Từ đồng nghĩa (Synonym) mới

Nếu bạn thấy AI không hiểu một món ăn mà user hay gõ (ví dụ user hay gọi "coca" thay vì "Nước giải khát Coca-Cola"), bạn chỉ cần thực hiện 2 bước sau:

### Bước 2.1: Thêm Synonym vào Neo4j
Truy cập vào giao diện quản trị Neo4j (Neo4j Browser hoặc Bloom), và chạy lệnh Cypher sau:

```cypher
// 1. Tìm Product Node cần gán (vd: tìm Coca-Cola)
MATCH (p:Product) WHERE p.name CONTAINS 'Coca-Cola' RETURN p;

// 2. Gán Synonym mới vào Product đó
MATCH (p:Product {productId: <NHẬP_ID_TÌM_ĐƯỢC>})
MERGE (s:Synonym {name: 'coca'})
MERGE (s)-[:MAPS_TO]->(p);
```

*Lưu ý:*
- Bạn **KHÔNG** cần phải thêm các phiên bản viết hoa, viết thường, hoặc không dấu (như "Coca", "COCA", "cô ca", "co ca"). Hệ thống Java Cache tự động sinh ra mọi biến thể chuẩn hóa.
- Bạn có thể gán nhiều Synonym cho 1 Product.
- Nếu muốn xóa một Synonym bị gán nhầm:
  ```cypher
  MATCH (s:Synonym {name: 'coca'})-[r:MAPS_TO]->(p:Product)
  DELETE r, s;
  ```

### Bước 2.2: Làm mới (Refresh) AI Keyword Cache trên Server
Mặc định Cache sẽ được load 1 lần khi Backend khởi động. Để nạp ngay Synonym vừa thêm vào hệ thống đang chạy trực tiếp (Live), hãy gọi API (hoặc yêu cầu backend dev setup 1 nút bấm trên Admin UI gọi API này):

```http
POST /api/admin/ai/refresh-dictionary
Authorization: Bearer <ADMIN_TOKEN>
```

(API này sẽ gọi hàm `foodDictionaryService.refreshDictionary()`). Hệ thống sử dụng cơ chế `AtomicReference` để hoán đổi (Swap) Dictionary trên RAM chưa tới 1 milliseconds, hoàn toàn **không gây gián đoạn** hay rớt request chat của khách hàng hiện tại.

## 3. Quản lý Đụng độ Từ khóa (Keyword Collision)
Thuật toán **Longest-Match-First** bằng Aho-Corasick tự động ưu tiên những từ dài nhất.
Ví dụ:
- Synonym 1: "Cá nục" -> Trỏ tới Sản phẩm A
- Synonym 2: "Cá nục bông" -> Trỏ tới Sản phẩm B
Nếu người dùng nhập: "cho mình cá nục bông", hệ thống sẽ *ưu tiên tuyệt đối* Sản phẩm B và tự động bỏ qua sự tồn tại của Sản phẩm A. Admin không cần phải cài đặt độ ưu tiên thủ công.

## 4. Xử lý Teencode thông dụng
Hiện tại Service đã được code cứng một số teencode quốc dân phổ biến (vd: `ko` = không, `dc` = được, `vs` = với).
Nếu có những teencode *của riêng tên món ăn* (ví dụ "bm" = bánh mì, "ts" = trà sữa), bạn cứ việc add "bm" và "ts" làm Synonym Node cho Bánh Mì và Trà Sữa trong Neo4j. AI sẽ hiểu ngay lập tức.
