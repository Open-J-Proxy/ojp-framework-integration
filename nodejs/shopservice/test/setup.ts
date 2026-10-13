import 'reflect-metadata';
import * as fs from 'fs';
import { createApp, AppContext } from '../src/app';
import {
  createCatalogDataSource,
  createCheckoutDataSource,
  DatabaseConfig,
} from '../src/config/database';

let sharedContext: AppContext | null = null;

export function getDbConfig(): DatabaseConfig {
  if (fs.existsSync('/tmp/mssql-test-config.json')) {
    const data = JSON.parse(fs.readFileSync('/tmp/mssql-test-config.json', 'utf8'));
    return {
      host: data.host,
      port: data.port,
      username: data.username,
      password: data.password,
      catalogDbName: 'nodejs_catalog_db',
      checkoutDbName: 'nodejs_checkout_db',
    };
  }
  return {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '1433', 10),
    username: process.env.DB_USER || 'sa',
    password: process.env.DB_PASSWORD || 'TestPassword123!',
    catalogDbName: 'nodejs_catalog_db',
    checkoutDbName: 'nodejs_checkout_db',
  };
}

export async function getTestContext(): Promise<AppContext> {
  if (
    sharedContext &&
    sharedContext.catalogDataSource.isInitialized &&
    sharedContext.checkoutDataSource.isInitialized
  ) {
    return sharedContext;
  }

  const config = getDbConfig();
  const catalogDataSource = createCatalogDataSource(config);
  const checkoutDataSource = createCheckoutDataSource(config);

  await catalogDataSource.initialize();
  await checkoutDataSource.initialize();

  sharedContext = createApp(catalogDataSource, checkoutDataSource);
  return sharedContext;
}

export async function closeTestContext(): Promise<void> {
  if (sharedContext) {
    if (sharedContext.catalogDataSource.isInitialized) {
      await sharedContext.catalogDataSource.destroy();
    }
    if (sharedContext.checkoutDataSource.isInitialized) {
      await sharedContext.checkoutDataSource.destroy();
    }
    sharedContext = null;
  }
}
