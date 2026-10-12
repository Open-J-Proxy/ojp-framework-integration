import * as fs from 'fs';
import { StartedMSSQLServerContainer } from '@testcontainers/mssqlserver';

declare global {
  // eslint-disable-next-line no-var
  var __MSSQL_CONTAINER__: StartedMSSQLServerContainer | undefined;
}

export default async function globalTeardown(): Promise<void> {
  if (globalThis.__MSSQL_CONTAINER__) {
    console.log('\n[Jest GlobalTeardown] Stopping SQL Server container...');
    await globalThis.__MSSQL_CONTAINER__.stop();
    console.log('[Jest GlobalTeardown] Container stopped.');
  }

  if (fs.existsSync('/tmp/mssql-test-config.json')) {
    try {
      fs.unlinkSync('/tmp/mssql-test-config.json');
    } catch {
      // ignore
    }
  }
}
