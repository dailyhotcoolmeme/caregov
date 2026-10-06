import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

export const accountId = '4c0f5d706177b84ade4d424a08ec46e8';
export const bucket = 'caregov-media-apac';
export const publicBase = 'https://caregov-ota.dailyhotcoolmeme.workers.dev';
export function cloudflareEnv() {
  const token = process.env.CLOUDFLARE_API_TOKEN?.trim()
    || readFileSync(join(homedir(), '.config/ootd/cf-api-token'), 'utf8').trim();
  if (!token) throw new Error('Cloudflare credential unavailable');
  return { ...process.env, CLOUDFLARE_API_TOKEN: token };
}
