# Hướng dẫn phân loại Ingredient trong Catalog

## 1. Mục tiêu
Catalog món ăn trong SmartGrocery dùng trường `ingredients` dạng danh sách object thay vì suy luận theo thứ tự mảng. Mỗi ingredient phải có:

- `name`: tên nguyên liệu
- `role`: giá trị enum cố định

Hệ thống hiện hỗ trợ 2 giá trị:

- `PRIMARY`: thành phần chính, quyết định bản chất món
- `SECONDARY`: thành phần phụ trợ, gia vị, topping, thành phần bổ trợ hoặc thành phần thay thế nhẹ

## 2. Tiêu chí phân loại
Dùng các quy tắc sau để gán role:

- `PRIMARY` khi nguyên liệu là phần lõi của món, chiếm vai trò chính về cấu trúc món, protein/chất nền chính, hoặc là thành phần bắt buộc để nhận diện món.
- `SECONDARY` khi nguyên liệu chỉ tạo hương vị, hỗ trợ cấu trúc, dùng ít, có thể thiếu mà món vẫn được nhận diện hợp lệ.
- Nếu một món có nhiều thành phần chính, tất cả các thành phần đó đều có thể gắn `PRIMARY`.
- Không dùng vị trí trong mảng để quyết định vai trò.

## 3. Quy tắc cập nhật dữ liệu
Khi thêm hoặc sửa món trong catalog:

- Phải điền `role` cho từng ingredient.
- Không để ingredient có `name` rỗng hoặc `role` rỗng.
- Tránh đổi thứ tự ingredient như một cách biểu thị ý nghĩa nghiệp vụ.
- Nếu có ingredient từng nằm ở `main_ingredients` cũ, mặc định chuyển sang `PRIMARY`.
- Nếu có ingredient từng nằm ở `optional_ingredients` cũ, mặc định chuyển sang `SECONDARY`.

## 4. Ví dụ
```json
{
  "name": "Miến sả ớt",
  "ingredients": [
    { "name": "cá", "role": "PRIMARY" },
    { "name": "bún", "role": "PRIMARY" },
    { "name": "giá đỗ", "role": "PRIMARY" },
    { "name": "mắm tôm", "role": "SECONDARY" },
    { "name": "mồng tơi", "role": "SECONDARY" }
  ]
}
```

## 5. Kiểm tra dữ liệu
Hệ thống sẽ kiểm tra schema khi load catalog:

- Ingredient typed mới phải có đủ `name` và `role`
- Nếu thiếu, backend sẽ báo lỗi để tránh dữ liệu catalog không nhất quán
- Dữ liệu legacy vẫn được đọc tạm thời qua `main_ingredients` và `optional_ingredients`, nhưng không nên dùng cho bản cập nhật mới

## 6. Script curation riêng
Để xử lý các món mơ hồ, hãy dùng script curation thay vì sửa tay trực tiếp trong file catalog:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\curate-meal-catalog.ps1 -InputPath src/main/resources/data/international_food_dataset_1000_vi.json -OutputPath src/main/resources/data/international_food_dataset_1000_vi.curated.json -OverridesPath scripts/meal-catalog-curation-overrides.json -ReportPath scripts/meal-catalog-curation-report.json
```

- Script ưu tiên override theo `id` hoặc tên chuẩn hóa.
- Nếu không có override, script dùng heuristics để phân loại rồi mới fallback về dữ liệu legacy.
- File override riêng là nơi ghi nhận các món mơ hồ cần cố định role để bảo đảm nhất quán.

## 7. Gợi ý vận hành
- Khi review món mới, hãy hỏi: nguyên liệu nào là lõi của món, nguyên liệu nào chỉ là phụ trợ.
- Nếu món vẫn có thể được nhận diện khi bỏ một ingredient, ingredient đó thường là `SECONDARY`.
- Nếu bỏ ingredient đó thì món không còn đúng bản chất, ingredient đó nên là `PRIMARY`.
