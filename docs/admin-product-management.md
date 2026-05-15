# Admin Product Management API

Base path: `/api/v1/admin/products`

All endpoints require an authenticated `ADMIN` user. Public catalog endpoints under `/api/v1/products` only expose products with `status=ACTIVE`.

## Status Model

- Product statuses: `ACTIVE`, `HIDDEN`, `DELETED`.
- `ACTIVE`: visible in public catalog.
- `HIDDEN`: editable in admin but hidden from public catalog.
- `DELETED`: soft-deleted; visible only when admin filters `status=DELETED`.
- Restore moves a product back to `HIDDEN`.

## Endpoints

### List Products

`GET /api/v1/admin/products?search=&categoryId=&status=&page=&size=`

Returns a Spring `Page<ProductDto>`. When `status` is omitted, `DELETED` products are excluded.

### Create Product

`POST /api/v1/admin/products`

Content type: `multipart/form-data`

Required fields:

- `productCode`
- `name`
- `categoryId`
- `variantsJson`

Optional fields:

- `shortDescription`
- `description`
- `originCountry`
- `status`
- `isFeatured`
- `image`

`variantsJson` is a JSON array:

```json
[
  {
    "sku": "SKU-APPLE-RED-1KG",
    "barcode": "893000000001",
    "variantName": "Red apple bag",
    "color": "Red",
    "size": "1kg",
    "unit": "bag",
    "netPrice": 45000,
    "stock": 12,
    "status": "ACTIVE"
  }
]
```

Image files must be `image/jpeg`, `image/png`, or `image/webp`, max 2MB by default.

### Update Product

`PUT /api/v1/admin/products/{productId}`

Same multipart shape as create. Existing variants are matched by `id`; variants omitted from `variantsJson` are soft-deleted with `status=DELETED`.

### Change Visibility

`PATCH /api/v1/admin/products/{productId}/status`

```json
{
  "status": "HIDDEN",
  "reason": "Seasonal item"
}
```

Allowed status values here: `ACTIVE`, `HIDDEN`.

### Soft Delete

`DELETE /api/v1/admin/products/{productId}`

```json
{
  "reason": "Discontinued"
}
```

### Restore

`POST /api/v1/admin/products/{productId}/restore`

```json
{
  "reason": "Back in assortment"
}
```

### Export Excel

`GET /api/v1/admin/products/export?search=&categoryId=&status=`

Returns `.xlsx` with columns: ID, code, name, category, status, featured, variant SKU, color, size, unit, price, stock, updatedAt.
