WITH target_products(product_code, sku, variant_name, unit, net_price, stock) AS (
    VALUES
        ('P_VEG_02', 'FIX-P_VEG_02', 'Goi 500g', 'Goi 500g', 18000.00, 100),
        ('P_VEG_03', 'FIX-P_VEG_03', 'Goi 500g', 'Goi 500g', 22000.00, 100),
        ('P_FRU_01', 'FIX-P_FRU_01', 'Tui 1kg', 'Tui 1kg', 42000.00, 100),
        ('P_FRU_02', 'FIX-P_FRU_02', 'Tui 1kg', 'Tui 1kg', 28000.00, 100),
        ('P_MEAT_01', 'FIX-P_MEAT_01', 'Khay 500g', 'Khay 500g', 98000.00, 80),
        ('P_DRI_01', 'FIX-P_DRI_01', 'Lon 330ml', 'Lon 330ml', 12000.00, 150)
),
main_warehouse AS (
    SELECT id AS warehouse_id
    FROM warehouses
    WHERE code = 'WH_MAIN'
    ORDER BY id
    LIMIT 1
),
inserted_variants AS (
    INSERT INTO product_variants (
        product_id,
        sku,
        variant_name,
        unit,
        package_size,
        net_price,
        compare_at_price,
        vat_percent,
        status,
        created_at,
        updated_at
    )
    SELECT
        p.product_id,
        tp.sku,
        tp.variant_name,
        tp.unit,
        tp.variant_name,
        tp.net_price,
        ROUND(tp.net_price * 1.10, 2),
        8.00,
        'ACTIVE',
        NOW(),
        NOW()
    FROM target_products tp
    JOIN products p ON p.product_code = tp.product_code
    WHERE NOT EXISTS (
        SELECT 1
        FROM product_variants pv
        WHERE pv.product_id = p.product_id
          AND UPPER(pv.status) = 'ACTIVE'
    )
    ON CONFLICT (sku) DO UPDATE
        SET status = 'ACTIVE',
            net_price = EXCLUDED.net_price,
            compare_at_price = EXCLUDED.compare_at_price,
            unit = EXCLUDED.unit,
            variant_name = EXCLUDED.variant_name,
            package_size = EXCLUDED.package_size,
            updated_at = NOW()
    RETURNING variant_id, sku
)
INSERT INTO inventory_stocks (
    warehouse_id,
    variant_id,
    available_quantity,
    reserved_quantity,
    updated_at
)
SELECT
    mw.warehouse_id,
    iv.variant_id,
    tp.stock,
    0,
    NOW()
FROM inserted_variants iv
JOIN target_products tp ON tp.sku = iv.sku
CROSS JOIN main_warehouse mw
WHERE NOT EXISTS (
    SELECT 1
    FROM inventory_stocks ist
    WHERE ist.warehouse_id = mw.warehouse_id
      AND ist.variant_id = iv.variant_id
);
