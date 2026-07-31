import { v7 as uuidv7 } from 'uuid';
import { ApiError } from '../errors.js';

export class SettingsService {
  constructor(repository) { this.repository = repository; }

  async get(userId) {
    const settings = await this.repository.findSettings(userId);
    if (!settings) throw new ApiError(401, 'auth.session_revoked', 'The session has been revoked.');
    return settings;
  }

  async replace({ userId, revision, settings }) {
    const result = await this.repository.updateSettings({ userId, revision, settings, nextRevision: uuidv7() });
    if (result.status === 'conflict') throw new ApiError(409, 'conflict.revision_mismatch', 'The settings have changed. Read them again before retrying.');
    return result.settings;
  }
}
