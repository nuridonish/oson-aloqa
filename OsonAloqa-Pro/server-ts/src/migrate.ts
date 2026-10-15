import { initDb } from './db';
import { log } from './logger';

async function run() {
  await initDb();
  log.info('Migrations complete');
  process.exit(0);
}

run().catch(e => {
  log.error(e);
  process.exit(1);
});
