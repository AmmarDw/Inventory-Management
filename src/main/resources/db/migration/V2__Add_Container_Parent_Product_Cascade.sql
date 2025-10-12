-- V1__Add_Container_Parent_Product_Cascade.sql
-- Adds ON DELETE CASCADE to the parent_product_id foreign key in the container table
-- to ensure database-level cleanup when a parent product is deleted.

-- 1. Find the actual name of the existing FK constraint on parent_product_id.
-- The name is often auto-generated (e.g., FK..., fk_..., constraint_...).
-- You MUST replace 'fk_container_parent_product_id' with the ACTUAL name found in your database schema.
-- Common ways to find it:
-- MySQL:
-- SELECT CONSTRAINT_NAME
-- FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
-- WHERE TABLE_SCHEMA = 'your_actual_database_name'
--   AND TABLE_NAME = 'container'
--   AND COLUMN_NAME = 'parent_product_id'
--   AND REFERENCED_TABLE_NAME = 'product';


-- 2. Drop the existing foreign key constraint
ALTER TABLE container DROP FOREIGN KEY FKbnobuq840pjujmggnjgh1faue;

-- 3. Re-add the foreign key constraint with ON DELETE CASCADE
-- Use the same name or a new descriptive one (e.g., fk_container_parent_product_cascade)
ALTER TABLE container
ADD CONSTRAINT fk_container_parent_product_cascade -- Or use the original name if preferred
FOREIGN KEY (parent_product_id) REFERENCES product(product_id)
ON DELETE CASCADE;