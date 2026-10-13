import { MSSQLServerContainer, StartedMSSQLServerContainer } from '@testcontainers/mssqlserver';
import * as fs from 'fs';
import { ensureDatabasesExist } from '../src/config/database';

declare global {
  // eslint-disable-next-line no-var
  var __MSSQL_CONTAINER__: StartedMSSQLServerContainer | undefined;
}

export default async function globalSetup(): Promise<void> {
  // If host and port are explicitly specified and no container needed
  if (process.env.DB_HOST && process.env.DB_PORT) {
    const config = {
      host: process.env.DB_HOST,
      port: parseInt(process.env.DB_PORT, 10),
      username: process.env.DB_USER || 'sa',
      password: process.env.DB_PASSWORD || 'TestPassword123!',
      catalogDbName: 'nodejs_catalog_db',
      checkoutDbName: 'nodejs_checkout_db',
    };
    fs.writeFileSync('/tmp/mssql-test-config.json', JSON.stringify(config));
    await ensureDatabasesExist(config);
    return;
  }

  console.log('\n[Jest GlobalSetup] Starting SQL Server 2022 container...');
  const container = await new MSSQLServerContainer('mcr.microsoft.com/mssql/server:2022-latest')
    .acceptLicense()
    .withPassword('TestPassword123!')
    .start();

  globalThis.__MSSQL_CONTAINER__ = container;

  const config = {
    host: container.getHost(),
    port: container.getPort(),
    username: container.getUsername(),
    password: container.getPassword(),
    catalogDbName: 'nodejs_catalog_db',
    checkoutDbName: 'nodejs_checkout_db',
  };

  fs.writeFileSync('/tmp/mssql-test-config.json', JSON.stringify(config));
  console.log(`[Jest GlobalSetup] SQL Server started on ${config.host}:${config.port}`);

  console.log('[Jest GlobalSetup] Ensuring databases exist...');
  await ensureDatabasesExist(config);
  console.log('[Jest GlobalSetup] Setup complete.');
}
