import { log } from '../logger';
import { config } from '../config';

let fcm: any = null;

async function initFcm() {
  if (fcm !== null) return fcm;
  if (!config.fcmCredentialsFile) {
    fcm = false;
    return fcm;
  }
  try {
    // Барои фаъолкунӣ:  npm install firebase-admin
    const importFn = new Function('m', 'return import(m)') as (m: string) => Promise<any>;
    const admin = await importFn('firebase-admin');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const serviceAccount = require(config.fcmCredentialsFile);
    admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
    fcm = admin;
    log.info('FCM initialized');
  } catch (e) {
    log.warn('FCM not available (npm install firebase-admin to enable):', (e as Error).message);
    fcm = false;
  }
  return fcm;
}

export async function pushTo(phone: string, pushToken: string, title: string, body: string) {
  const admin = await initFcm();
  if (!admin || !pushToken) return;
  try {
    await admin.messaging().send({
      token: pushToken,
      notification: { title, body },
      data: { phone }
    });
  } catch (e) {
    log.warn('FCM push failed:', (e as Error).message);
  }
}
