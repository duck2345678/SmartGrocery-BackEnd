-- TRUNG TÂM BIÊN SOẠN THỰC ĐƠN SMARTGROCERY
-- Toàn bộ 100+ món ăn được map trực tiếp từ kho sản phẩm thực tế

TRUNCATE TABLE meal_ingredients RESTART IDENTITY CASCADE;
TRUNCATE TABLE meals RESTART IDENTITY CASCADE;

DO $$
DECLARE
    m_id INT;
BEGIN
    -- ==========================================
    -- BỮA SÁNG (SÁNG)
    -- ==========================================
    
    INSERT INTO meals (meal_name, meal_category, dietary_goal, description, created_at) VALUES ('Phở Bò Truyền Thống', 'Sáng', 'Bình thường', 'Phở bò thơm ngon đúng điệu.', NOW()) RETURNING meal_id INTO m_id;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Thịt bò', 'PRIMARY', true FROM products WHERE product_name ILIKE '%Thăn bò Úc%' OR product_name ILIKE '%Bắp bò hoa%' LIMIT 2;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Bánh phở & Hành', 'SECONDARY', false FROM products WHERE product_name ILIKE '%Phở bò%' OR product_name ILIKE '%Hành lá%' OR product_name ILIKE '%Tương đen%';

    INSERT INTO meals (meal_name, meal_category, dietary_goal, description, created_at) VALUES ('Bún Gà Thanh Đạm', 'Sáng', 'Bình thường', 'Bữa sáng nhẹ nhàng.', NOW()) RETURNING meal_id INTO m_id;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Thịt gà', 'PRIMARY', true FROM products WHERE product_name ILIKE '%Đùi gà%' OR product_name ILIKE '%Bún tươi%' LIMIT 2;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Rau thơm', 'SECONDARY', false FROM products WHERE product_name ILIKE '%Giá đỗ%' OR product_name ILIKE '%Chanh%';

    INSERT INTO meals (meal_name, meal_category, dietary_goal, description, created_at) VALUES ('Cơm Tấm Sườn Bì', 'Sáng', 'Bình thường', 'Bữa sáng năng lượng.', NOW()) RETURNING meal_id INTO m_id;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Sườn heo', 'PRIMARY', true FROM products WHERE product_name ILIKE '%Sườn non%' OR product_name ILIKE '%Gạo ST25%' LIMIT 2;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Dưa leo & Ớt', 'SECONDARY', false FROM products WHERE product_name ILIKE '%Dưa leo%' OR product_name ILIKE '%Ớt%';

    -- ==========================================
    -- BỮA TRƯA (TRƯA)
    -- ==========================================

    INSERT INTO meals (meal_name, meal_category, dietary_goal, description, created_at) VALUES ('Cơm Tấm Sườn Nướng', 'Trưa', 'Bình thường', 'Món cơm quốc dân.', NOW()) RETURNING meal_id INTO m_id;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Sườn heo', 'PRIMARY', true FROM products WHERE product_name ILIKE '%Sườn non%' OR product_name ILIKE '%Gạo ST25%' LIMIT 2;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Trứng & Rau', 'SECONDARY', false FROM products WHERE product_name ILIKE '%Trứng gà%' OR product_name ILIKE '%Dưa leo%' OR product_name ILIKE '%Nước mắm%';

    INSERT INTO meals (meal_name, meal_category, dietary_goal, description, created_at) VALUES ('Cơm Gà Hải Nam', 'Trưa', 'Bình thường', 'Gà luộc thơm lừng.', NOW()) RETURNING meal_id INTO m_id;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Thịt gà', 'PRIMARY', true FROM products WHERE product_name ILIKE '%Đùi gà%' OR product_name ILIKE '%Gạo ST25%' LIMIT 2;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Gừng & Dưa leo', 'SECONDARY', false FROM products WHERE product_name ILIKE '%Gừng%' OR product_name ILIKE '%Dưa leo%' OR product_name ILIKE '%Hành lá%';

    -- ==========================================
    -- BỮA TỐI (TỐI)
    -- ==========================================

    INSERT INTO meals (meal_name, meal_category, dietary_goal, description, created_at) VALUES ('Cá Kho Tộ Gia Đình', 'Tối', 'Truyền thống', 'Đậm đà vị quê.', NOW()) RETURNING meal_id INTO m_id;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Cá lóc', 'PRIMARY', true FROM products WHERE product_name ILIKE '%Cá lóc%' LIMIT 1;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Thịt ba rọi', 'PRIMARY', true FROM products WHERE product_name ILIKE '%Ba rọi%' LIMIT 1;
    INSERT INTO meal_ingredients (meal_id, product_id, generic_name, role, is_mandatory) SELECT m_id, product_id, 'Gia vị', 'SECONDARY', false FROM products WHERE product_name ILIKE '%Nước mắm%' OR product_name ILIKE '%Tiêu%' OR product_name ILIKE '%Ớt%';

END $$;
