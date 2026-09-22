WITH cart_variant_counts AS (
    SELECT ci.cart_id, COUNT(DISTINCT ci.variant_id) AS variant_count
    FROM cart_items ci
    GROUP BY ci.cart_id
),
cart_candidates AS (
    SELECT ci.cart_id, i.warehouse_id
    FROM cart_items ci
    JOIN inventory i ON i.variant_id = ci.variant_id
    GROUP BY ci.cart_id, i.warehouse_id
    HAVING COUNT(DISTINCT ci.variant_id) = (
        SELECT cvc.variant_count
        FROM cart_variant_counts cvc
        WHERE cvc.cart_id = ci.cart_id
    )
),
unambiguous_carts AS (
    SELECT cart_id, (ARRAY_AGG(warehouse_id ORDER BY warehouse_id))[1] AS warehouse_id
    FROM cart_candidates
    GROUP BY cart_id
    HAVING COUNT(*) = 1
)
UPDATE carts c
SET warehouse_id = uc.warehouse_id
FROM unambiguous_carts uc
WHERE c.cart_id = uc.cart_id
  AND c.warehouse_id IS NULL;

WITH order_variant_counts AS (
    SELECT oi.order_id, COUNT(DISTINCT oi.variant_id) AS variant_count
    FROM order_items oi
    GROUP BY oi.order_id
),
order_candidates AS (
    SELECT oi.order_id, i.warehouse_id
    FROM order_items oi
    JOIN inventory i ON i.variant_id = oi.variant_id
    GROUP BY oi.order_id, i.warehouse_id
    HAVING COUNT(DISTINCT oi.variant_id) = (
        SELECT ovc.variant_count
        FROM order_variant_counts ovc
        WHERE ovc.order_id = oi.order_id
    )
),
unambiguous_orders AS (
    SELECT order_id, (ARRAY_AGG(warehouse_id ORDER BY warehouse_id))[1] AS warehouse_id
    FROM order_candidates
    GROUP BY order_id
    HAVING COUNT(*) = 1
)
UPDATE orders o
SET warehouse_id = uo.warehouse_id
FROM unambiguous_orders uo
WHERE o.order_id = uo.order_id
  AND o.warehouse_id IS NULL;
