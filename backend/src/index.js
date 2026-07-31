import { loadConfig } from './config.js';
import { createLogger } from './http.js';
import { PostgresIdentityRepository } from './repositories/postgres-identity-repository.js';
import { startBackend } from './server.js';

const config = loadConfig();
const logger = createLogger();
const repository = new PostgresIdentityRepository(config.databaseUrl);
const backend = await startBackend({ config, repository, logger });
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.once(signal, () => {
    backend.shutdown(signal)
      .then(() => process.exit(0))
      .catch((error) => { logger.error({ errorName: error?.name }, 'backend shutdown failed'); process.exit(1); });
  });
}
