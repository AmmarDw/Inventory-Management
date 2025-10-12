-- Step 1: Add the new 'volume' column, allowing it to be NULL temporarily.
-- This is necessary to handle existing rows in the 'product' table.
ALTER TABLE product
ADD COLUMN volume DECIMAL(20, 10) NULL;

-- Step 2: Update all existing products with a sensible default volume.
-- 🚨 NOTE: '1.0' is a placeholder value. Change it to a volume that makes
-- sense for your existing products.
UPDATE product
SET volume = 1.0
WHERE volume IS NULL;

-- Step 3: Now that all rows have a value, modify the column to be NOT NULL.
ALTER TABLE product
MODIFY COLUMN volume DECIMAL(20, 10) NOT NULL;

-- Step 4: Add a CHECK constraint to ensure the volume is always positive.
-- This enforces the @DecimalMin(value = "0.0", inclusive = false) rule at the database level.
ALTER TABLE product
ADD CONSTRAINT chk_product_volume CHECK (volume > 0);