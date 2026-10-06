import { execFileSync } from 'node:child_process';
import { accountId, bucket, cloudflareEnv } from './cloudflare-env.mjs';

const env = cloudflareEnv();
const base = `https://api.cloudflare.com/client/v4/accounts/${accountId}/r2/buckets`;
const headers = { Authorization: `Bearer ${env.CLOUDFLARE_API_TOKEN}`, 'Content-Type': 'application/json' };
const exists = await fetch(`${base}/${bucket}`, { headers });
if (exists.status === 404) {
  const created = await fetch(base, { method: 'POST', headers, body: JSON.stringify({ name: bucket, locationHint: 'apac' }) });
  if (!created.ok) throw new Error(`Bucket creation failed: HTTP ${created.status}`);
} else if (!exists.ok) throw new Error(`Bucket access failed: HTTP ${exists.status}`);
console.log(`Bucket ready: ${bucket}`);
execFileSync('npx', ['--yes', 'wrangler', 'deploy', '--config', 'cloudflare/ota/wrangler.toml'], { env, stdio: 'inherit' });
