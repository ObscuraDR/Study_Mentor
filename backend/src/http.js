import { v7 as uuidv7 } from 'uuid';
import { ApiError, errorEnvelope } from './errors.js';

export function requestContext(logger) {
  return (req, res, next) => {
    const requestId = `req_${uuidv7()}`;
    req.requestId = requestId;
    const startedAt = process.hrtime.bigint();
    res.setHeader('X-Request-Id', requestId);
    res.on('finish', () => {
      logger.info({
        requestId,
        method: req.method,
        path: req.path,
        status: res.statusCode,
        durationMs: Number(process.hrtime.bigint() - startedAt) / 1_000_000,
        actorId: req.auth?.userId,
      }, 'request completed');
    });
    next();
  };
}

export function respond(res, data) {
  return res.status(200).json({ data, meta: { requestId: res.req.requestId } });
}

export function errorHandler(logger) {
  return (error, req, res, _next) => {
    if (error instanceof ApiError) return res.status(error.status).json(errorEnvelope(error, req.requestId));
    if (error?.type === 'entity.parse.failed') {
      return res.status(422).json(errorEnvelope(new ApiError(422, 'validation.invalid_request', 'The request body must be valid JSON.'), req.requestId));
    }
    logger.error({ requestId: req.requestId, errorName: error?.name, errorMessage: error?.message }, 'unhandled request error');
    return res.status(500).json(errorEnvelope(new ApiError(500, 'server.internal', 'An unexpected server error occurred.'), req.requestId));
  };
}

export function createLogger(sink = console) {
  return {
    info(context, message) { sink.log(JSON.stringify({ level: 'info', message, ...context })); },
    warn(context, message) { sink.warn(JSON.stringify({ level: 'warn', message, ...context })); },
    error(context, message) { sink.error(JSON.stringify({ level: 'error', message, ...context })); },
  };
}
