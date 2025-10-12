-- Step 1: Add the new employee_id column, allowing NULL values by default.
ALTER TABLE inventory_stock ADD COLUMN employee_id INTEGER NULL;

-- Step 2: Add the foreign key constraint.
-- This will succeed immediately because the new column is NULL for all existing rows,
-- and a foreign key constraint allows NULL values.
ALTER TABLE inventory_stock
ADD CONSTRAINT FKn89lpe0gcu344quserb0t5o5p
FOREIGN KEY (employee_id) REFERENCES user(user_id);