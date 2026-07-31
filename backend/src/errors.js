export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function validationError(details) {
  return new ApiError(422, 'validation.invalid_request', 'The request is invalid.', details);
}

export function errorEnvelope(error, requestId) {
  const body = { code: error.code, message: error.message, requestId };
  if (error.details?.length) body.details = error.details;
  return { error: body };
}
