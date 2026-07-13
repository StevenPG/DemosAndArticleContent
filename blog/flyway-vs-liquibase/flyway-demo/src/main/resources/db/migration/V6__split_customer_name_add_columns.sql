-- Step 1 of the expand/contract split of customers.name
ALTER TABLE customers ADD COLUMN first_name TEXT;
ALTER TABLE customers ADD COLUMN last_name TEXT;
