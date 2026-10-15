import dotenv from 'dotenv';
dotenv.config();

export const config = {
  port: Number(process.env.PORT || 8080),
  nodeEnv: process.env.NODE_ENV || 'development',
  pg: {
    host: process.env.PGHOST || 'localhost',
    port: Number(process.env.PGPORT || 5432),
    user: process.env.PGUSER || 'postgres',
    password: process.env.PGPASSWORD || '',
    database: process.env.PGDATABASE || 'osonaloqa'
  },
  accessSecret: process.env.JWT_ACCESS_SECRET || '',
  refreshSecret: process.env.JWT_REFRESH_SECRET || '',
  accessTtlSec: Number(process.env.ACCESS_TTL_SEC || 900),
  refreshTtlSec: Number(process.env.REFRESH_TTL_SEC || 7776000),
  otp: {
    ttl: Number(process.env.OTP_TTL_SECONDS || 300) * 1000,
    maxAttempts: Number(process.env.OTP_MAX_ATTEMPTS || 5)
  },
  sms: {
    provider: (process.env.SMS_PROVIDER || 'none').toLowerCase(),
    apiKey: process.env.SMS_API_KEY || '',
    from: process.env.SMS_FROM || '',
    urlTemplate: process.env.SMS_URL_TEMPLATE || ''
  },
  corsOrigins: (process.env.CORS_ORIGINS || '*').split(',').map(s => s.trim()),
  mediaDir: process.env.MEDIA_DIR || 'uploads',
  mediaMaxMb: Number(process.env.MEDIA_MAX_MB || 25),
  fcmCredentialsFile: process.env.FCM_CREDENTIALS_FILE || '',
  tlsCert: process.env.TLS_CERT || '',
  tlsKey: process.env.TLS_KEY || ''
};

if (config.nodeEnv === 'production') {
  const missing: string[] = [];
  if (!config.accessSecret) missing.push('JWT_ACCESS_SECRET');
  if (!config.refreshSecret) missing.push('JWT_REFRESH_SECRET');
  if (missing.length) {
    // eslint-disable-next-line no-console
    console.error('FATAL: missing env:', missing.join(', '));
    process.exit(1);
  }
}
