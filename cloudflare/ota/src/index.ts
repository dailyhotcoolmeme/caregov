interface Env { BUCKET: R2Bucket }

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== 'GET' && request.method !== 'HEAD') return new Response('Method not allowed', { status: 405 });
    const path = new URL(request.url).pathname;
    const manifest = path === '/android.json';
    const apk = /^\/apk\/caregov-[a-zA-Z0-9._-]+\.apk$/.test(path);
    if (!manifest && !apk) return new Response('Not found', { status: 404 });
    const key = manifest ? 'ota/pointer/android.json' : path.slice(1);
    const object = await env.BUCKET.get(key);
    if (!object) return new Response('Not found', { status: 404, headers: { 'Cache-Control': 'no-store' } });
    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set('Content-Type', manifest ? 'application/json' : 'application/vnd.android.package-archive');
    headers.set('Cache-Control', manifest ? 'no-store, max-age=0' : 'public, max-age=31536000, immutable');
    headers.set('Content-Length', String(object.size));
    headers.set('X-Content-Type-Options', 'nosniff');
    if (apk) headers.set('Content-Disposition', `attachment; filename="${key.split('/').pop()}"`);
    return new Response(request.method === 'HEAD' ? null : object.body, { headers });
  },
};
