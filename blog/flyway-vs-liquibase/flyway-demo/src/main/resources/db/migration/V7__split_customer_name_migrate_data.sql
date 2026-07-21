-- Step 2: migrate data into the new columns
UPDATE customers
SET first_name = split_part(name, ' ', 1),
    last_name  = NULLIF(substring(name FROM position(' ' IN name) + 1), name);
