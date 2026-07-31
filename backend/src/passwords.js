import argon2 from 'argon2';

const options = Object.freeze({ type: argon2.argon2id, memoryCost: 65_536, timeCost: 3, parallelism: 1 });

export function hashPassword(password) { return argon2.hash(password, options); }
export function verifyPassword(hash, password) { return argon2.verify(hash, password); }
