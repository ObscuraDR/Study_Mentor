import { createApp } from './app.js';

export async function startBackend({ config, repository, logger }) {
  try {
    await repository.verifyConnection();
  } catch (error) {
    logger.error({ errorName: error?.name }, 'database connection failed during startup');
    await repository.close();
    throw new Error('Database connection is unavailable. Check DATABASE_URL and PostgreSQL health.');
  }
  const app = createApp({ config, repository, logger });
  const server = await new Promise((resolve) => {
    const current = app.listen(config.port, () => {
      logger.info({ port: current.address().port, environment: config.environment }, 'backend listening');
      resolve(current);
    });
  });
  let closing;
  return {
    app,
    server,
    async shutdown(signal = 'shutdown') {
      if (!closing) {
        closing = new Promise((resolve, reject) => {
          logger.info({ signal }, 'backend shutting down');
          server.close(async (error) => {
            if (error) return reject(error);
            try { await repository.close(); resolve(); } catch (closeError) { reject(closeError); }
          });
        });
      }
      return closing;
    },
  };
}
