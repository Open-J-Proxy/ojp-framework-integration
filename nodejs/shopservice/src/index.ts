import 'reflect-metadata';
import { ensureDatabasesExist, createCatalogDataSource, createCheckoutDataSource } from './config/database';
import { createApp } from './app';

const PORT = parseInt(process.env.PORT || '8080', 10);

async function bootstrap() {
  console.log('Ensuring SQL Server databases exist...');
  await ensureDatabasesExist();

  console.log('Initializing Catalog and Checkout DataSources...');
  const catalogDataSource = createCatalogDataSource();
  const checkoutDataSource = createCheckoutDataSource();

  await catalogDataSource.initialize();
  await checkoutDataSource.initialize();

  const { app } = createApp(catalogDataSource, checkoutDataSource);

  app.listen(PORT, () => {
    console.log(`ShopService listening on port ${PORT}`);
  });
}

if (require.main === module) {
  bootstrap().catch((err) => {
    console.error('Failed to start application:', err);
    process.exit(1);
  });
}

export { bootstrap };
