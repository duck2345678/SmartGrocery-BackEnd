# Staff Order Lease Contract

## Mục tiêu
- Chống dẫm chân khi nhiều nhân viên cùng nhận đơn.
- Dùng lease + heartbeat để tự giải phóng đơn khi thiết bị staff mất kết nối.

## Trạng thái chính
- `PENDING` -> `ASSIGNED` -> `PICKING` -> `PICKED` -> `READY_FOR_DELIVERY`

## DB fields (orders)
- `assignee_id` (nullable, FK users.user_id)
- `lease_expires_at` (nullable timestamp)

## API

### 1) Queue đơn chờ nhận
- `GET /api/v1/staff/orders/queue`
- Trả về đơn `PENDING` mà chưa có assignee hoặc lease đã hết hạn.

### 2) Assign đơn
- `POST /api/v1/staff/orders/{orderId}/assign`
- Thành công: trả `orderId`, `assigneeId`, `status=ASSIGNED`, `leaseExpiresAt`.
- Nếu đơn đã bị staff khác giữ lease: trả `409`.

### 3) Heartbeat lease
- `POST /api/v1/staff/orders/{orderId}/heartbeat`
- Gia hạn lease thêm 10 phút.
- Nếu không đúng assignee hoặc lease hết hạn: trả lỗi unauthorized.

### 4) Release đơn
- `POST /api/v1/staff/orders/{orderId}/release`
- Chỉ assignee hiện tại mới được release.
- Trả về trạng thái `PENDING`.

## TTL đề xuất
- Lease TTL: 10 phút
- Client gửi heartbeat mỗi 3 phút

## Phase 2
- `POST /api/v1/staff/orders/{id}/complete-picking` (batch payload)
- Validate rule thay thế: `substitute.price <= original.price`

## Implemented Next Step
- `GET /api/v1/staff/orders/{id}/pick-list`
  - Trả danh sách theo `aisle_location ASC` để tối ưu đường đi nhặt hàng.
- `GET /api/v1/staff/orders/{id}/substitutions?orderItemId={orderItemId}`
  - Lọc theo category, `price <= originalUnitPrice`, `stock > 0`.
  - Trả về danh sách tối đa 10 lựa chọn cho dropdown/modal.
- `POST /api/v1/staff/orders/{id}/complete-picking`
  - Validate lease owner + lease chưa hết hạn.
  - Validate `actualQuantity <= orderedQuantity`.
  - Nếu thay thế: backend tự đọc giá hiện tại của SKU thay thế và bắt buộc `substituteCurrentPrice <= originalUnitPrice`.
  - Cập nhật tồn kho:
    - Món gốc: hoàn lại số lượng không lấy hoặc hoàn lại toàn bộ khi thay thế.
    - Món thay thế: trừ tồn theo số lượng thực lấy.
  - Cập nhật `order_items` picked state (`picked_quantity`, `is_substituted`, `substituted_variant_id`, `substitution_reason`).
  - Cập nhật trạng thái đơn sang `PICKED`, reset lease.
