import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contractPath = new URL('../openapi/ai-study-mentor.v1.openapi.json', import.meta.url);
const contractText = await readFile(contractPath, 'utf8');
const contract = JSON.parse(contractText);

// JSON.parse silently keeps only the LAST of any duplicate object keys, discarding
// everything defined under earlier occurrences of the same key. That is exactly how the
// contract once lost ~30 response definitions under a duplicate top-level
// `components.responses` key. This scans the raw text (not the already-collapsed parsed
// object) so a repeat of that mistake fails loudly instead of silently dropping data.
function findDuplicateKeys(text) {
  const duplicates = [];
  const stack = [];
  let inString = false;
  let escape = false;
  let line = 1;

  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '\n') line++;

    if (inString) {
      if (escape) escape = false;
      else if (ch === '\\') escape = true;
      else if (ch === '"') inString = false;
      continue;
    }

    if (ch === '"') {
      inString = true;
      const stringStart = i + 1;
      let j = stringStart;
      let esc = false;
      while (j < text.length && (esc || text[j] !== '"')) {
        esc = !esc && text[j] === '\\' ? true : false;
        j++;
      }
      const value = text.slice(stringStart, j);
      // Peek past the closing quote for a colon (mod whitespace) to decide if this string is
      // an object key rather than a plain string value.
      let k = j + 1;
      while (k < text.length && /\s/.test(text[k])) k++;
      if (text[k] === ':' && stack.length > 0 && stack[stack.length - 1].type === 'object') {
        const frame = stack[stack.length - 1];
        if (frame.keys.has(value)) {
          duplicates.push({ key: value, line, path: [...frame.path, value].join('.') });
        } else {
          frame.keys.add(value);
        }
        frame.pendingKey = value;
      }
      i = j; // resume scanning right after the closing quote
      inString = false;
      continue;
    }

    if (ch === '{') {
      const parentPath = stack.length > 0 ? stack[stack.length - 1].path : [];
      const pendingKey = stack.length > 0 ? stack[stack.length - 1].pendingKey : undefined;
      stack.push({ type: 'object', keys: new Set(), path: pendingKey ? [...parentPath, pendingKey] : parentPath });
    } else if (ch === '[') {
      const parentPath = stack.length > 0 ? stack[stack.length - 1].path : [];
      const pendingKey = stack.length > 0 ? stack[stack.length - 1].pendingKey : undefined;
      stack.push({ type: 'array', path: pendingKey ? [...parentPath, pendingKey] : parentPath });
    } else if (ch === '}' || ch === ']') {
      stack.pop();
    }
  }

  return duplicates;
}

function resolveReference(reference) {
  assert.ok(reference.startsWith('#/'), `Only local component references are allowed: ${reference}`);
  return reference.slice(2).split('/').reduce((value, segment) => value?.[segment], contract);
}

function collectReferences(value, references = []) {
  if (Array.isArray(value)) {
    value.forEach((item) => collectReferences(item, references));
  } else if (value && typeof value === 'object') {
    if (typeof value.$ref === 'string') references.push(value.$ref);
    Object.values(value).forEach((item) => collectReferences(item, references));
  }
  return references;
}

function responseSchema(path, method, status) {
  const response = contract.paths[path][method].responses[status];
  const resolved = response.$ref ? resolveReference(response.$ref) : response;
  return resolved.content['application/json'].schema;
}

test('has no duplicate object keys anywhere in the contract JSON', () => {
  const duplicates = findDuplicateKeys(contractText);
  assert.deepEqual(duplicates, [], `Duplicate JSON keys silently discard the first occurrence's content (JSON.parse keeps only the last). Found: ${JSON.stringify(duplicates)}`);
});

test('is valid JSON OpenAPI 3.1 with unique operation IDs and resolvable local references', () => {
  assert.equal(contract.openapi, '3.1.0');
  assert.equal(contract.servers[0].url.endsWith('/api/v1'), true);

  const operationIds = Object.values(contract.paths)
    .flatMap((path) => Object.values(path))
    .filter((operation) => operation && typeof operation === 'object' && operation.operationId)
    .map((operation) => operation.operationId);
  assert.equal(new Set(operationIds).size, operationIds.length);

  for (const reference of collectReferences(contract)) {
    assert.ok(resolveReference(reference), `Unresolvable reference: ${reference}`);
  }
});

test('uses the canonical UUIDv7 and RFC 3339 timestamp schemas', () => {
  const { Uuid, Timestamp } = contract.components.schemas;
  assert.equal(Uuid.type, 'string');
  assert.equal(Uuid.format, 'uuid');
  assert.match(Uuid.pattern, /-7\[/);
  assert.equal(Timestamp.type, 'string');
  assert.equal(Timestamp.format, 'date-time');
  assert.match(Timestamp.description, /RFC 3339/);
  assert.deepEqual(contract.components.schemas.LearningEvent.properties.userId, { $ref: '#/components/schemas/Uuid' });
  assert.deepEqual(contract.components.schemas.User.properties.id, { $ref: '#/components/schemas/Uuid' });
});

test('uses standard success and error envelopes with stable error codes', () => {
  const { ErrorEnvelope, ResponseMeta, ErrorCode } = contract.components.schemas;
  assert.deepEqual(ErrorEnvelope.required, ['error']);
  assert.deepEqual(ResponseMeta.required, ['requestId']);
  assert.ok(ErrorCode.enum.includes('auth.refresh_token_reused'));
  assert.ok(ErrorCode.enum.includes('learning.idempotency_key_reused'));
  assert.ok(ErrorCode.enum.includes('learning.unknown_lesson'));
  assert.ok(ErrorCode.enum.includes('quiz.idempotency_key_reused'));
  assert.ok(ErrorCode.enum.includes('quiz.incomplete_attempt'));

  for (const [path, method, status] of [
    ['/auth/login', 'post', '200'],
    ['/auth/me', 'get', '200'],
    ['/me/profile', 'get', '200'],
    ['/me/settings', 'get', '200'],
    ['/me/progress', 'get', '200'],
    ['/subjects', 'get', '200'],
    ['/topics/{topicId}/lessons', 'get', '200'],
    ['/learning-events', 'post', '201'],
    ['/quizzes', 'get', '200'],
    ['/quizzes/{quizId}', 'get', '200'],
    ['/quiz-attempts', 'post', '201'],
  ]) {
    const schema = responseSchema(path, method, status);
    const envelope = resolveReference(schema.$ref);
    assert.deepEqual(envelope.required, ['data', 'meta']);
  }
});

test('defines secure, platform-specific refresh behavior without password responses', () => {
  const refresh = contract.paths['/auth/refresh'].post;
  assert.match(refresh.description, /Secure, HttpOnly, SameSite cookie/);
  assert.match(refresh.description, /android.*required/i);
  assert.equal(contract.components.securitySchemes.bearerAuth.scheme, 'bearer');

  const refreshRequest = contract.components.schemas.AndroidRefreshRequest;
  assert.deepEqual(refreshRequest.required, ['refreshToken']);
  for (const sessionName of ['Session', 'RefreshedSession']) {
    const session = contract.components.schemas[sessionName];
    assert.equal(session.required.includes('refreshToken'), false);
    assert.equal(Object.hasOwn(session.properties, 'password'), false);
  }
  assert.equal(Object.hasOwn(contract.components.schemas.User.properties, 'password'), false);
    assert.equal(Object.hasOwn(contract.components.schemas.User.properties, 'avatarKey'), false);
});

test('requires bearer authorization, revisions, and idempotent immutable learning-event submission', () => {
  for (const path of ['/auth/me', '/me/profile', '/me/settings', '/me/progress', '/subjects', '/subjects/{subjectId}', '/topics/{topicId}/lessons', '/learning-events', '/quizzes', '/quizzes/{quizId}', '/quiz-attempts']) {
    const operation = contract.paths[path][path === '/learning-events' || path === '/quiz-attempts' ? 'post' : 'get'];
    assert.deepEqual(operation.security, [{ bearerAuth: [] }]);
  }

  const event = contract.paths['/learning-events'].post;
  const key = event.parameters.find((parameter) => parameter.$ref === '#/components/parameters/IdempotencyKey');
  assert.ok(key);
  assert.match(event.description, /immutable learning event/);
  assert.ok(event.responses['201']);
  assert.ok(event.responses['200']);
  assert.ok(event.responses['409']);
  const attempt = contract.paths['/quiz-attempts'].post;
  assert.ok(attempt.parameters.some((parameter) => parameter.$ref === '#/components/parameters/IdempotencyKey'));
  assert.match(attempt.description, /server-owned and forbidden/u);
  assert.ok(attempt.responses['201']);
  assert.ok(attempt.responses['200']);
  assert.ok(attempt.responses['409']);
  assert.deepEqual(contract.components.schemas.ProgressProjection.required, ['completedLessons', 'totalLessons', 'completedTopics', 'totalTopics', 'completedSubjects', 'totalSubjects', 'totalXp', 'learningTimeSeconds', 'completionPercentage']);

    // QuizQuestionResult correctOptionId is always required and non-null
    const qqr = contract.components.schemas.QuizQuestionResult;
    assert.ok(qqr.required.includes('correctOptionId'), 'QuizQuestionResult.correctOptionId must be required');
    assert.equal(qqr.properties.correctOptionId.$ref, '#/components/schemas/Uuid');

  const profileUpdate = contract.paths['/me/profile'].patch;
  const settingsUpdate = contract.paths['/me/settings'].put;
  assert.ok(profileUpdate.parameters.some((parameter) => parameter.$ref === '#/components/parameters/IfMatch'));
  assert.ok(settingsUpdate.parameters.some((parameter) => parameter.$ref === '#/components/parameters/IfMatch'));
});

test('defines the private campaign projection with open-guided versioned semantics', () => {
  const operation = contract.paths['/me/campaign'].get;
  assert.deepEqual(operation.security, [{ bearerAuth: [] }]);
  assert.deepEqual(Object.keys(operation.responses).sort(), ['200', '401']);
  assert.equal(contract.components.responses.Campaign.content['application/json'].schema.$ref, '#/components/schemas/CampaignEnvelope');
  const projection = contract.components.schemas.CampaignProjection;
  assert.equal(projection.properties.campaignKey.const, 'core-learning-map');
  assert.equal(projection.properties.campaignVersion.const, 'campaign-v1');
  assert.equal(projection.properties.catalogVersion.const, 'catalog-v1');
  assert.equal(projection.properties.accessPolicy.const, 'open-guided');
  assert.equal(projection.properties.recommendedNodeId.anyOf[1].type, 'null');
  assert.deepEqual(contract.components.schemas.CampaignLessonNode.properties.state.enum, ['not_started', 'recommended', 'completed']);
});

test('defines private server-authoritative streak recovery eligibility and bodyless claim endpoints', () => {
  const read = contract.paths['/me/streak-recovery'].get;
  const claim = contract.paths['/me/streak-recoveries'].post;
  assert.deepEqual(read.security, [{ bearerAuth: [] }]);
  assert.deepEqual(claim.security, [{ bearerAuth: [] }]);
  assert.equal(claim.requestBody, undefined);
  assert.ok(claim.parameters.some((parameter) => parameter.$ref === '#/components/parameters/IdempotencyKey'));
  assert.deepEqual(Object.keys(claim.responses).sort(), ['200', '201', '401', '409', '422']);
  for (const responseName of ['StreakRecoveryEligibility', 'StreakRecoveryClaimCreated', 'StreakRecoveryClaimReplay', 'StreakRecoveryUnavailable']) {
    assert.ok(contract.components.responses[responseName].content['application/json'].examples);
  }
  const eligibility = contract.components.schemas.StreakRecoveryEligibilityProjection;
  assert.equal(eligibility.oneOf.length, 2);
  assert.equal(eligibility.oneOf.every((branch) => branch.additionalProperties === false), true);
  assert.ok(contract.components.schemas.ErrorCode.enum.includes('streak_recovery.ineligible'));
});
