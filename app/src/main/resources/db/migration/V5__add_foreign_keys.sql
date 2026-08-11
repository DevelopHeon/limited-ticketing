ALTER TABLE sale_products
    ADD CONSTRAINT fk_sale_products_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT;

ALTER TABLE inventories
    ADD CONSTRAINT fk_inventories_sale_product
        FOREIGN KEY (sale_product_id) REFERENCES sale_products (id) ON DELETE RESTRICT;

ALTER TABLE purchases
    ADD CONSTRAINT fk_purchases_member
        FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE RESTRICT;

ALTER TABLE purchases
    ADD CONSTRAINT fk_purchases_sale_product
        FOREIGN KEY (sale_product_id) REFERENCES sale_products (id) ON DELETE RESTRICT;

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_purchase
        FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE RESTRICT;

ALTER TABLE reservation_queue_entries
    ADD CONSTRAINT fk_reservation_queue_entries_member
        FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE RESTRICT;

ALTER TABLE reservation_queue_entries
    ADD CONSTRAINT fk_reservation_queue_entries_sale_product
        FOREIGN KEY (sale_product_id) REFERENCES sale_products (id) ON DELETE RESTRICT;

ALTER TABLE reservation_queue_entries
    ADD CONSTRAINT fk_reservation_queue_entries_purchase
        FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE RESTRICT;

CREATE INDEX idx_sale_products_product_id
    ON sale_products (product_id);

CREATE INDEX idx_purchases_sale_product_id
    ON purchases (sale_product_id);

CREATE INDEX idx_payments_purchase_id
    ON payments (purchase_id);

CREATE INDEX idx_queue_entry_sale_product_id
    ON reservation_queue_entries (sale_product_id);

CREATE INDEX idx_queue_entry_purchase_id
    ON reservation_queue_entries (purchase_id);
