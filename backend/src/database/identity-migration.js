import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

async function applyMigration(pool, name) {
  const migrationPath = fileURLToPath(new URL(`../../migrations/${name}`, import.meta.url));
  await pool.query(await readFile(migrationPath, 'utf8'));
}

export async function applyIdentityMigration(pool) {
  await applyMigration(pool, '001_identity_foundation.sql');
}

export async function applySchemaMigrations(pool) {
  await applyIdentityMigration(pool);
  await applyMigration(pool, '002_shared_settings.sql');
  await applyMigration(pool, '003_learning_foundation.sql');
  await applyMigration(pool, '004_quiz_attempt_foundation.sql');
  await applyMigration(pool, '005_ai_tutor_foundation.sql');
  await applyMigration(pool, '006_flashcard_foundation.sql');
  await applyMigration(pool, '007_catalog_expansion.sql');
  await applyMigration(pool, '008_course_quiz_content_expansion.sql');
  await applyMigration(pool, '009_game_based_learning_foundation.sql');
  await applyMigration(pool, '010_streak_recovery_persistence.sql');
  await applyMigration(pool, '011_full_product_v1.sql');
}
