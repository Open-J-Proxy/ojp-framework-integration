import { DataSource, DataSourceOptions } from 'typeorm';
import * as mssql from 'mssql';
import { User } from '../entity/catalog/User';
import { Product } from '../entity/catalog/Product';
import { Review } from '../entity/catalog/Review';
import { Order } from '../entity/checkout/Order';
import { OrderItem } from '../entity/checkout/OrderItem';

export interface DatabaseConfig {
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  catalogDbName?: string;
  checkoutDbName?: string;
}

export async function ensureDatabasesExist(config?: DatabaseConfig): Promise<void> {
  const host = config?.host || process.env.DB_HOST || 'localhost';
  const port = config?.port || parseInt(process.env.DB_PORT || '1433', 10);
  const user = config?.username || process.env.DB_USER || 'sa';
  const password = config?.password || process.env.DB_PASSWORD || 'TestPassword123!';
  const catalogDb = config?.catalogDbName || process.env.CATALOG_DB_NAME || 'catalog_db';
  const checkoutDb = config?.checkoutDbName || process.env.CHECKOUT_DB_NAME || 'checkout_db';

  const sqlConfig: mssql.config = {
    user,
    password,
    server: host,
    port,
    database: 'master',
    options: {
      encrypt: false,
      trustServerCertificate: true,
    },
  };

  const pool = await mssql.connect(sqlConfig);
  try {
    await pool.request().query(`
      IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = '${catalogDb}')
        CREATE DATABASE [${catalogDb}];
      IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = '${checkoutDb}')
        CREATE DATABASE [${checkoutDb}];
    `);
  } finally {
    await pool.close();
  }
}

export function createCatalogDataSource(
  config?: DatabaseConfig,
  overrides?: Partial<DataSourceOptions>
): DataSource {
  const host = config?.host || process.env.DB_HOST || 'localhost';
  const port = config?.port || parseInt(process.env.DB_PORT || '1433', 10);
  const username = config?.username || process.env.DB_USER || 'sa';
  const password = config?.password || process.env.DB_PASSWORD || 'TestPassword123!';
  const database = config?.catalogDbName || process.env.CATALOG_DB_NAME || 'catalog_db';

  return new DataSource({
    type: 'mssql',
    host,
    port,
    username,
    password,
    database,
    entities: [User, Product, Review],
    synchronize: true,
    logging: false,
    options: {
      encrypt: false,
      trustServerCertificate: true,
    },
    ...overrides,
  } as DataSourceOptions);
}

export function createCheckoutDataSource(
  config?: DatabaseConfig,
  overrides?: Partial<DataSourceOptions>
): DataSource {
  const host = config?.host || process.env.DB_HOST || 'localhost';
  const port = config?.port || parseInt(process.env.DB_PORT || '1433', 10);
  const username = config?.username || process.env.DB_USER || 'sa';
  const password = config?.password || process.env.DB_PASSWORD || 'TestPassword123!';
  const database = config?.checkoutDbName || process.env.CHECKOUT_DB_NAME || 'checkout_db';

  return new DataSource({
    type: 'mssql',
    host,
    port,
    username,
    password,
    database,
    entities: [Order, OrderItem],
    synchronize: true,
    logging: false,
    options: {
      encrypt: false,
      trustServerCertificate: true,
    },
    ...overrides,
  } as DataSourceOptions);
}
