import rateLimit from 'express-rate-limit';
import { ApiError, errorEnvelope } from './errors.js';

function handler(req, res) {
  const error = new ApiError(429, 'rate_limit.exceeded', 'Too many requests. Please try again later.');
  res.status(429).json(errorEnvelope(error, req.requestId));
}

function limiter({ windowMs, max }) {
  return rateLimit({ windowMs, max, standardHeaders: true, legacyHeaders: false, handler });
}

export function createRateLimiters(environment) {
  return {
    global: limiter({ windowMs: 60_000, max: environment === 'production' ? 120 : 600 }),
    credentials: limiter({ windowMs: 60_000, max: environment === 'production' ? 10 : 100 }),
    refresh: limiter({ windowMs: 60_000, max: environment === 'production' ? 30 : 200 }),
  };
}
