import https from 'https';
import { config } from '../config';
import { log } from '../logger';

export async function sendSms(phone: string, text: string): Promise<boolean> {
  try {
    if (config.sms.provider === 'smsru' && config.sms.apiKey) {
      const url = `https://sms.ru/sms/send?api_id=${config.sms.apiKey}&to=${encodeURIComponent(phone)}&msg=${encodeURIComponent(text)}&json=1`;
      await httpsGet(url);
      return true;
    }
    if (config.sms.provider === 'irsms' && config.sms.urlTemplate) {
      const url = config.sms.urlTemplate
        .replace('{KEY}', encodeURIComponent(config.sms.apiKey))
        .replace('{TO}', encodeURIComponent(phone))
        .replace('{TEXT}', encodeURIComponent(text));
      await httpsGet(url);
      return true;
    }
    if (config.sms.provider === 'twilio' && config.sms.apiKey && config.sms.from) {
      const [sid, secret] = config.sms.apiKey.split(':');
      const params = new URLSearchParams({ To: phone, From: config.sms.from, Body: text }).toString();
      await httpsPost(`https://api.twilio.com/2010-04-01/Accounts/${sid}/Messages.json`, params, {
        Authorization: 'Basic ' + Buffer.from(sid + ':' + secret).toString('base64'),
        'Content-Type': 'application/x-www-form-urlencoded'
      });
      return true;
    }
  } catch (e) {
    log.error('sms send failed:', (e as Error).message);
  }
  return false;
}

function httpsGet(url: string): Promise<string> {
  return new Promise((resolve, reject) => {
    https.get(url, r => {
      let b = '';
      r.on('data', c => (b += c));
      r.on('end', () => resolve(b));
    }).on('error', reject);
  });
}

function httpsPost(url: string, body: string, headers: Record<string, string>): Promise<string> {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = https.request(
      { hostname: u.hostname, path: u.pathname + u.search, method: 'POST', headers },
      r => {
        let b = '';
        r.on('data', c => (b += c));
        r.on('end', () => resolve(b));
      }
    );
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}
