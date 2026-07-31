import { Pool } from 'pg';
import { loadConfig } from './config.js';
import { applySchemaMigrations } from './database/identity-migration.js';

const target = process.argv[2] ?? '--development';
if (!['--development', '--test'].includes(target)) throw new Error('Usage: node src/migrate.js --development|--test');
const connectionString = target === '--test' ? process.env.TEST_DATABASE_URL : process.env.DATABASE_URL;
const config = loadConfig({ environment: target === '--test' ? 'test' : process.env.NODE_ENV, databaseUrl: connectionString });
if (target === '--test') {
  const databaseName = new URL(config.databaseUrl).pathname.replace(/^\//u, '');
  if (!databaseName.endsWith('_test')) throw new Error('TEST_DATABASE_URL must target a database whose name ends in _test.');
}
const pool = new Pool({ connectionString: config.databaseUrl });
try {
  await applySchemaMigrations(pool);
  console.log(`P4 schema migrations applied to ${target.slice(2)} database.`);
} finally { await pool.end(); }
