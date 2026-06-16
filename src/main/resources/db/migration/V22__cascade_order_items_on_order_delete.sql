DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT tc.constraint_name
    INTO constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
    WHERE tc.constraint_type = 'FOREIGN KEY'
      AND tc.table_schema = 'public'
      AND tc.table_name = 'order_items'
      AND kcu.column_name = 'order_id'
    LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.order_items DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE public.order_items
    ADD CONSTRAINT fk_order_items_order_cascade
    FOREIGN KEY (order_id)
    REFERENCES public.orders(order_id)
    ON DELETE CASCADE;
