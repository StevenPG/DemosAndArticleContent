-- Data migration: everything older than 30 days is considered archived
UPDATE orders SET status = 'ARCHIVED' WHERE created_at < NOW() - INTERVAL '30 days';
