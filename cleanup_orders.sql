-- =============================================
-- Xóa toàn bộ đơn hàng cũ (dùng cho dev/test)
-- Chạy trên Supabase SQL Editor
-- =============================================

-- 1. Xóa các bảng con trước (foreign key dependencies)
DELETE FROM payment_transactions;
DELETE FROM payments;
DELETE FROM order_status_histories;
DELETE FROM order_issues;
DELETE FROM order_assignments;
DELETE FROM order_items;

-- 2. Xóa bảng đơn hàng chính
DELETE FROM orders;

-- Xác nhận
SELECT 'Done! All orders cleaned up.' AS result;
