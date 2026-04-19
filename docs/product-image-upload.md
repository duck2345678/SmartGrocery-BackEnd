# Upload ảnh sản phẩm (Admin)

## Mục tiêu
- Admin upload ảnh khi tạo/cập nhật sản phẩm.
- Backend lưu file vào `public/uploads/products/` và lưu đường dẫn vào cột `products.image`.
- Client/Admin UI hiển thị ảnh từ URL `/uploads/products/{filename}`.

## DB Migration
- File SQL: `migrations/2026_04_19_01_add_products_image.sql`
- Câu lệnh:
  - `ALTER TABLE products ADD COLUMN image VARCHAR(255);`

## Static files
- Backend expose static files:
  - URL: `GET /uploads/products/**`
  - Source: thư mục `public/uploads/products/` (config `app.upload.products-dir`)

## API

### 1) Tạo sản phẩm (multipart)
- `POST /api/v1/admin/products`
- `Content-Type: multipart/form-data`
- Fields:
  - `productCode`, `name`, `categoryId`, `sku`, `netPrice`
  - Optional: `shortDescription`, `description`, `originCountry`, `status`, `isFeatured`, `barcode`, `variantName`, `unit`, `stock`
  - File: `image` (jpg/png/webp, tối đa 2MB)

### 2) Cập nhật sản phẩm (multipart)
- `PUT /api/v1/admin/products/{productId}`
- `Content-Type: multipart/form-data`
- Fields tương tự create, `image` optional.

### 3) Upload ảnh riêng (multipart)
- `POST /api/v1/admin/products/{productId}/image`
- `Content-Type: multipart/form-data`
- File: `image` (jpg/png/webp, tối đa 2MB)

## Validation
- Định dạng: `jpg`, `png`, `webp` (content-type: image/jpeg|image/png|image/webp)
- Kích thước tối đa: 2MB (`app.upload.products-max-bytes`, default 2097152)

## Hướng dẫn Admin (UI)
- Chọn file ảnh tại form tạo/sửa sản phẩm.
- UI hiển thị preview trước khi submit.
- Nếu file không hợp lệ hoặc >2MB, backend trả về 400 với message tiếng Việt.

