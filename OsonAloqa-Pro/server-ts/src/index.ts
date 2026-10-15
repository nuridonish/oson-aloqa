import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import { config } from './config';
import { initDb } from './db';
import { log } from './logger';
import { initWs } from './realtime/ws';

import authRoutes from './routes/auth';
import userRoutes from './routes/users';
import messageRoutes from './routes/messages';
import mediaRoutes from './routes/media';
import socialRoutes from './routes/social';
import groupRoutes from './routes/groups';
import dashboardRoutes from './routes/dashboard';
import callRoutes from './routes/calls';

async function main() {
  await initDb();

  const app = Fastify({
    logger: { level: config.nodeEnv === 'production' ? 'info' : 'warn' },
    trustProxy: true,
    bodyLimit: 2 * 1024 * 1024
  });

  await app.register(helmet, { contentSecurityPolicy: false });
  await app.register(cors, { origin: config.corsOrigins.includes('*') ? true : config.corsOrigins });
  await app.register(rateLimit, {
    global: true,
    max: 300,
    timeWindow: '1 minute',
    errorResponseBuilder: () => ({ ok: false, error: 'rate_limited' })
  });

  // octet-stream (media upload — мутобиқи APK)
  app.addContentTypeParser('application/octet-stream', { parseAs: 'buffer' }, (_req, body, done) => {
    done(null, body);
  });

  app.get('/', async () => ({
    name: 'Oson Aloqa Server',
    version: '3.0',
    api: '/v1 (compat: бе /v1 ҳам кор мекунад)',
    sms_provider: config.sms.provider
  }));

  app.get('/health', async () => {
    try {
      const { query } = await import('./db');
      await query('SELECT 1');
      return { ok: true, status: 'healthy', uptime: process.uptime() };
    } catch (e) {
      return { ok: false, status: 'unhealthy' };
    }
  });

  app.register(authRoutes);
  app.register(userRoutes);
  app.register(messageRoutes);
  app.register(mediaRoutes);
  app.register(socialRoutes);
  app.register(groupRoutes);
  app.register(dashboardRoutes);
  app.register(callRoutes);

  await app.ready();
  initWs(app.server);

  app.listen({ port: config.port, host: '0.0.0.0' })
    .then(() => log.info(`Oson Aloqa v3 server on :${config.port} | env=${config.nodeEnv} | sms=${config.sms.provider}`))
    .catch(e => {
      log.error('listen failed:', e);
      process.exit(1);
    });
}

main().catch(e => {
  log.error('Fatal:', e);
  process.exit(1);
});


