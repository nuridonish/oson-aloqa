const ts = () => new Date().toISOString();
export const log = {
  info: (...a: any[]) => console.log(`[${ts()}] [INFO]`, ...a),
  warn: (...a: any[]) => console.warn(`[${ts()}] [WARN]`, ...a),
  error: (...a: any[]) => console.error(`[${ts()}] [ERROR]`, ...a)
};
