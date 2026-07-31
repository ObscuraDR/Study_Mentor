import { v7 as uuidv7 } from 'uuid';
import { ApiError } from '../errors.js';
import { hashPassword, verifyPassword } from '../passwords.js';
import { createRefreshToken, hashRefreshToken, issueAccessToken } from '../tokens.js';

function timestamp(value) { return value.toISOString(); }
function publicUser(user) { return { id: user.id, displayName: user.displayName, email: user.email, createdAt: timestamp(new Date(user.createdAt)) }; }

export class AuthService {
  constructor(repository, config, now = () => new Date()) { this.repository = repository; this.config = config; this.now = now; }

  async register(input, platform) {
    const user = { id: uuidv7(), displayName: input.displayName, email: input.email, passwordHash: await hashPassword(input.password), role: 'user', createdAt: this.now() };
    const created = await this.repository.createUserWithProfile({
      user,
      profile: { revision: uuidv7() },
      settings: { locale: 'vi', dailyGoalTargetXp: 300, revision: uuidv7() },
    });
    if (!created) throw new ApiError(409, 'auth.email_already_registered', 'An account already exists for this email address.');
    return this.createSession(user, platform);
  }

  async login(input, platform) {
    const user = await this.repository.findUserByEmail(input.email);
    if (!user || !(await verifyPassword(user.passwordHash, input.password))) throw new ApiError(401, 'auth.invalid_credentials', 'The email or password is incorrect.');
    return this.createSession(user, platform);
  }

  async createSession(user, platform) {
    const now = this.now();
    const expiresAt = new Date(now.getTime() + this.config.refreshTokenTtlSeconds * 1000);
    const family = { id: uuidv7(), userId: user.id, clientPlatform: platform, expiresAt };
    const refreshToken = createRefreshToken();
    await this.repository.createSession({ family, session: { id: uuidv7(), tokenHash: hashRefreshToken(refreshToken), expiresAt } });
    const access = issueAccessToken({ userId: user.id, familyId: family.id, role: user.role }, this.config, now);
    return { user: publicUser(user), accessToken: access.token, accessTokenExpiresAt: timestamp(access.expiresAt), refreshToken, refreshTokenExpiresAt: timestamp(expiresAt) };
  }

  async refresh(refreshToken) {
    const now = this.now();
    const expiresAt = new Date(now.getTime() + this.config.refreshTokenTtlSeconds * 1000);
    const nextRefreshToken = createRefreshToken();
    const result = await this.repository.rotateRefreshToken({ tokenHash: hashRefreshToken(refreshToken), nextSession: { id: uuidv7(), tokenHash: hashRefreshToken(nextRefreshToken), expiresAt } });
    if (result.status !== 'rotated') {
      const code = { invalid: 'auth.refresh_token_invalid', reused: 'auth.refresh_token_reused', revoked: 'auth.session_revoked', expired: 'auth.session_expired' }[result.status] ?? 'auth.refresh_token_invalid';
      throw new ApiError(401, code, 'The refresh session is no longer valid.');
    }
    const access = issueAccessToken({ userId: result.user.id, familyId: result.familyId, role: result.user.role }, this.config, now);
    return { accessToken: access.token, accessTokenExpiresAt: timestamp(access.expiresAt), refreshToken: nextRefreshToken, refreshTokenExpiresAt: timestamp(result.refreshTokenExpiresAt) };
  }
}
