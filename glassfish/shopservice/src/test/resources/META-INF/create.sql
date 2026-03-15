-- Create sequences first, then tables in FK-safe order (parent tables first).
-- Using sequences instead of IDENTITY columns avoids a known OJP proxy limitation:
-- the gRPC proxy strips IDENTITY/auto-increment semantics from DDL, so IDENTITY columns
-- end up as plain NOT NULL columns with no default.  With SEQUENCE generation EclipseLink
-- calls NEXTVAL explicitly before each INSERT and includes the ID in the statement,
-- which works correctly through the OJP proxy.
-- Each statement is on a single line: EclipseLink's create-source=script reader splits
-- on newlines, so a multi-line statement would be executed line-by-line.
CREATE SEQUENCE IF NOT EXISTS users_id_seq START WITH 1;
CREATE SEQUENCE IF NOT EXISTS products_id_seq START WITH 1;
CREATE SEQUENCE IF NOT EXISTS orders_id_seq START WITH 1;
CREATE SEQUENCE IF NOT EXISTS order_items_id_seq START WITH 1;
CREATE SEQUENCE IF NOT EXISTS reviews_id_seq START WITH 1;
CREATE TABLE users (id BIGINT NOT NULL, username VARCHAR(255) NOT NULL, email VARCHAR(255) NOT NULL, createdat TIMESTAMP, PRIMARY KEY (id), UNIQUE (username), UNIQUE (email));
CREATE TABLE products (id BIGINT NOT NULL, name VARCHAR(255) NOT NULL, price NUMERIC(38) NOT NULL, PRIMARY KEY (id));
CREATE TABLE orders (id BIGINT NOT NULL, orderdate TIMESTAMP NOT NULL, user_id BIGINT, PRIMARY KEY (id), CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id));
CREATE TABLE order_items (id BIGINT NOT NULL, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity INT NOT NULL, PRIMARY KEY (id), CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id), CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id));
CREATE TABLE reviews (id BIGINT NOT NULL, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, rating INT NOT NULL, comment VARCHAR(1000), PRIMARY KEY (id), CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(id), CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products(id));
