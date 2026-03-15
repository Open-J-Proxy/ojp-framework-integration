-- Drop all tables, using CASCADE to remove any FK constraints that reference them.
-- CASCADE ensures the drop succeeds even if a constraint in another table still
-- references this one (e.g. if a previous schema generation left partial state).
-- IF EXISTS keeps the script idempotent on a fresh database.
DROP TABLE IF EXISTS ORDER_ITEMS CASCADE;
DROP TABLE IF EXISTS REVIEWS CASCADE;
DROP TABLE IF EXISTS ORDERS CASCADE;
DROP TABLE IF EXISTS PRODUCTS CASCADE;
DROP TABLE IF EXISTS USERS CASCADE;
