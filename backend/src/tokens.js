import { createHash, randomBytes } from 'node:crypto';
import jwt from 'jsonwebtoken';

export function createRefreshToken() { return `rt.v1.${randomBytes(48).toString('base64url')}`; }
export function hashRefreshToken(token) { return createHash('sha256').update(token).digest('hex'); }

export function issueAccessToken({ userId, familyId, role }, config, now = new Date()) {
  const expiresAt = new Date(now.getTime() + config.accessTokenTtlSeconds * 1000);
  return {
    token: jwt.sign({ sub: userId, fid: familyId, role }, config.jwtAccessSecret, { algorithm: 'HS256', issuer: config.jwtIssuer, audience: config.jwtAudience, expiresIn: config.accessTokenTtlSeconds }),
    expiresAt,
  };
}

export function verifyAccessToken(token, config) {
  return jwt.verify(token, config.jwtAccessSecret, { algorithms: ['HS256'], issuer: config.jwtIssuer, audience: config.jwtAudience });
}
