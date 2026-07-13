-- Step 3: contract — enforce and drop the old column
ALTER TABLE customers ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE customers DROP COLUMN name;
