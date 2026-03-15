-- Drop child tables before parent tables to satisfy FK constraints.
-- Using IF EXISTS so this script is safe to run even when the schema is empty.
DROP TABLE IF EXISTS ORDER_ITEMS;
DROP TABLE IF EXISTS REVIEWS;
DROP TABLE IF EXISTS ORDERS;
DROP TABLE IF EXISTS PRODUCTS;
DROP TABLE IF EXISTS USERS;
