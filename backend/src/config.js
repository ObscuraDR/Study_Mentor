import 'dotenv/config';
import { randomBytes } from 'node:crypto';

const MINIMUM_SECRET_LENGTH = 32;
const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1']);

function required(name, value) {
  if (!value) throw new Error(`${name} must be configured`);
  return value;
}

function secret(name, value, environment) {
  if (value && value.length >= MINIMUM_SECRET_LENGTH) return value;
  if (environment === 'test') return randomBytes(48).toString('base64url');
  throw new Error(`${name} must be configured with at least ${MINIMUM_SECRET_LENGTH} characters`);
}

function databaseUrl(value, environment) {
  const configured = environment === 'test' && !value ? 'postgres://test/test' : required('DATABASE_URL', value);
  let parsed;
  try { parsed = new URL(configured); } catch { throw new Error('DATABASE_URL must be a valid PostgreSQL connection URL'); }
  if (!['postgres:', 'postgresql:'].includes(parsed.protocol) || !parsed.pathname || parsed.pathname === '/') {
    throw new Error('DATABASE_URL must be a PostgreSQL URL with a database name');
  }
  return configured;
}

function port(value) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65_535) throw new Error('PORT must be an integer between 1 and 65535');
  return parsed;
}

function boolean(name, value, fallback) {
  if (value === undefined || value === '') return fallback;
  if (value === true || value === 'true') return true;
  if (value === false || value === 'false') return false;
  throw new Error(`${name} must be true or false`);
}

function normalizeOrigin(value) {
  let parsed;
  try { parsed = new URL(value); } catch { throw new Error(`WEB_ORIGINS contains an invalid origin: ${value}`); }
  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.pathname !== '/' || parsed.search || parsed.hash || parsed.username || parsed.password) {
    throw new Error(`WEB_ORIGINS must contain absolute origins only: ${value}`);
  }
  return parsed.origin;
}

function isLoopbackOrigin(origin) {
  return LOOPBACK_HOSTS.has(new URL(origin).hostname);
}

function cookieDomain(value) {
  if (!value) return undefined;
  if (typeof value !== 'string' || !/^[a-z0-9.-]+$/iu.test(value) || value.includes('..') || value.startsWith('.') || value.endsWith('.')) {
    throw new Error('REFRESH_COOKIE_DOMAIN must be a plain host name without a scheme, path, or wildcard');
  }
  return value.toLowerCase();
}

export function loadConfig(overrides = {}) {
  const environment = overrides.environment ?? process.env.NODE_ENV ?? 'development';
  const rawOrigins = overrides.webOrigins ?? process.env.WEB_ORIGINS ?? 'http://localhost:3000';
  const webOrigins = rawOrigins.split(',').map((origin) => origin.trim()).filter(Boolean).map(normalizeOrigin);
  if (!webOrigins.length || new Set(webOrigins).size !== webOrigins.length) throw new Error('WEB_ORIGINS must contain one or more unique origins');
  const refreshCookieSecure = boolean('REFRESH_COOKIE_SECURE', overrides.refreshCookieSecure ?? process.env.REFRESH_COOKIE_SECURE, true);
  const allowInsecureLoopbackRefreshCookie = boolean('ALLOW_INSECURE_LOOPBACK_REFRESH_COOKIE', overrides.allowInsecureLoopbackRefreshCookie ?? process.env.ALLOW_INSECURE_LOOPBACK_REFRESH_COOKIE, false);
  if (['production', 'staging'].includes(environment) && (!refreshCookieSecure || allowInsecureLoopbackRefreshCookie)) {
    throw new Error('Production and staging require Secure refresh cookies and forbid the loopback exception');
  }
  if (!refreshCookieSecure && (!['development', 'test'].includes(environment) || !allowInsecureLoopbackRefreshCookie || !webOrigins.every(isLoopbackOrigin))) {
    throw new Error('Insecure refresh cookies require the explicitly enabled development/test loopback exception with loopback WEB_ORIGINS only');
  }
  if (refreshCookieSecure && allowInsecureLoopbackRefreshCookie) throw new Error('ALLOW_INSECURE_LOOPBACK_REFRESH_COOKIE requires REFRESH_COOKIE_SECURE=false');
  return Object.freeze({
    environment,
    port: port(overrides.port ?? process.env.PORT ?? 8080),
    databaseUrl: databaseUrl(overrides.databaseUrl ?? process.env.DATABASE_URL, environment),
    jwtAccessSecret: secret('JWT_ACCESS_SECRET', overrides.jwtAccessSecret ?? process.env.JWT_ACCESS_SECRET, environment),
    jwtIssuer: overrides.jwtIssuer ?? process.env.JWT_ISSUER ?? 'ai-study-mentor-api',
    jwtAudience: overrides.jwtAudience ?? process.env.JWT_AUDIENCE ?? 'ai-study-mentor-clients',
    accessTokenTtlSeconds: 15 * 60,
    refreshTokenTtlSeconds: 30 * 24 * 60 * 60,
    learningEventFutureToleranceSeconds: 5 * 60,
    webOrigins,
    refreshCookieSecure,
    allowInsecureLoopbackRefreshCookie,
    refreshCookieDomain: cookieDomain(overrides.refreshCookieDomain ?? process.env.REFRESH_COOKIE_DOMAIN),
  });
}

export function isLoopbackRequest(req) {
  const host = req.hostname?.toLowerCase();
  return LOOPBACK_HOSTS.has(host);
}
