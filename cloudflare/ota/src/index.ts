interface Env { BUCKET: R2Bucket }

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== 'GET' && request.method !== 'HEAD') return new Response('Method not allowed', { status: 405 });
    const path = new URL(request.url).pathname;
    if (path === '/manifest') {
      const platform = request.headers.get('expo-platform') || request.headers.get('exponent-platform');
      const runtime = request.headers.get('expo-runtime-version');
      if (platform !== 'android' || !runtime || !/^[a-zA-Z0-9._-]{1,80}$/.test(runtime)) return new Response('Invalid runtime or platform', { status: 400 });
      const object = await env.BUCKET.get(`ota/pointer/${runtime}/android.json`);
      if (!object) return new Response('No update', { status: 404, headers: { 'Cache-Control': 'no-store' } });
      const boundary = 'caregov-ota-boundary';
      const body = `--${boundary}\r\nContent-Disposition: form-data; name="manifest"\r\nContent-Type: application/json\r\n\r\n${await object.text()}\r\n--${boundary}--\r\n`;
      return new Response(request.method === 'HEAD' ? null : body, { headers: {
        'Content-Type': `multipart/mixed; boundary=${boundary}`, 'expo-protocol-version': '1',
        'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
      } });
    }
    const file = /^\/files\/(bundle|asset)-[A-Za-z0-9_-]{43}\.[a-z0-9]{1,8}$/.test(path);
    const manifest = path === '/android.json';
    const apk = /^\/apk\/caregov-[a-zA-Z0-9._-]+\.apk$/.test(path);
    if (!manifest && !apk && !file) return new Response('Not found', { status: 404 });
    const key = manifest ? 'ota/pointer/android.json' : file ? `ota${path}` : path.slice(1);
    const object = await env.BUCKET.get(key);
    if (!object) return new Response('Not found', { status: 404, headers: { 'Cache-Control': 'no-store' } });
    const headers = new Headers();
    object.writeHttpMetadata(headers);
    if (!file) headers.set('Content-Type', manifest ? 'application/json' : 'application/vnd.android.package-archive');
    headers.set('Cache-Control', manifest ? 'no-store, max-age=0' : 'public, max-age=31536000, immutable');
    headers.set('Content-Length', String(object.size));
    headers.set('X-Content-Type-Options', 'nosniff');
    if (apk) headers.set('Content-Disposition', `attachment; filename="${key.split('/').pop()}"`);
    return new Response(request.method === 'HEAD' ? null : object.body, { headers });
  },
};
