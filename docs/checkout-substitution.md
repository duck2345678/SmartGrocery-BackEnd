# Substitution Preference (Checkout)

## Mục tiêu
- Mỗi item trong đơn hàng lưu `allowSubstitution` để phục vụ picking/fulfillment khi thiếu hàng.
- Mobile gửi flag theo từng item khi checkout.

## Data Contract

### Endpoint
- `POST /api/v1/orders/checkout`

### Request
```json
{
  "addressId": 12,
  "paymentMethod": "COD",
  "customerNote": "Giao buổi sáng",
  "items": [
    { "variantId": 1001, "quantity": 2, "allowSubstitution": true },
    { "variantId": 1002, "quantity": 1, "allowSubstitution": false }
  ]
}
```

### Behavior
- Nếu `allowSubstitution` bị thiếu/null → mặc định `false`.
- Giá dùng để tính đơn là `variant.netPrice` hiện tại tại thời điểm checkout.

### Response
- `OrderDto.items[].allowSubstitution` phản ánh đúng flag đã lưu.

## Implementation Notes
- DTO request: `OrderItemRequest.allowSubstitution`
- Entity: `OrderItem.allowSubstitution` (boolean, default false)
- Mapping: `Boolean.TRUE.equals(request.allowSubstitution)`

