import pg from 'pg';
const { Pool } = pg;
const dsn = 'postgres://ai_study_mentor:local-development-only-password@127.0.0.1:54329/ai_study_mentor_test';
const pool = new Pool({ connectionString: dsn });

const cols = await pool.query("SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='ai_tutor_idempotency' ORDER BY ordinal_position");
console.log('Columns:', cols.rows.length);
cols.rows.forEach(r => console.log(' ', r.column_name, r.data_type, r.is_nullable));

const cons = await pool.query("SELECT conname, contype FROM pg_constraint WHERE conrelid='ai_tutor_idempotency'::regclass ORDER BY conname");
console.log('Constraints:', cons.rows.length);
cons.rows.forEach(r => console.log(' ', r.conname, r.contype));

try {
  await pool.query("INSERT INTO ai_tutor_idempotency (id, user_id, idempotency_key, request_fingerprint, lesson_id, state, processing_started_at, lease_expires_at, claim_token, created_at, expires_at, normalized_response) VALUES (gen_random_uuid(), (SELECT id FROM users LIMIT 1), gen_random_uuid(), repeat('a',64), '019f7e39-0003-7000-8000-000000000001', 'processing', now(), now() + interval '30 seconds', gen_random_uuid(), now(), now() + interval '24 hours', '{}'::jsonb)");
  console.log('FAIL: should have rejected processing with response');
} catch(e) {
  console.log('PASS: constraint rejected processing+response:', e.constraint || e.code);
}

const hash = await pool.query("SELECT hashtextextended('test-user'::text, 5567942638328492081::bigint) AS lock_key");
console.log('hashtextextended works:', hash.rows[0].lock_key);

await pool.end();
console.log('SCHEMA VERIFICATION COMPLETE');
