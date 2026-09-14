// Proxy transparente local: faz o papel do gateway do app hospedeiro (ADR-0008) para o cenário 15
// do exemplo, seguindo backend/docs/backend/proxy.md. Sem dependência: só `node:http`/`node:https`.
//
//   node proxy/server.mjs [--port 8787] [--target http://localhost:8080/api] [--prefix /pitaco]
//
// Também lê PITACO_PROXY_PORT, PITACO_PROXY_TARGET e PITACO_PROXY_PREFIX. O SDK aponta o `baseUrl`
// para `http://<host>:<porta><prefixo>` (é o que o seed escreve em EXPO_PUBLIC_PITACO_PROXY_BASE_URL).
//
// O que ele faz, e só isso:
// - repassa `POST {prefixo}/collect/*` para `{alvo}/collect/*`, com o mesmo restante do caminho e
//   da query; qualquer outra rota recebe 404 em JSON, sem tocar no Pitaco (nunca a superfície
//   administrativa, o actuator ou a documentação);
// - repassa os cabeçalhos do cliente sem alterar (`X-Pitaco-Key`, `X-Pitaco-Sdk-Version`,
//   `Content-Type`), menos os de salto e os de origem que o cliente não pode escolher;
// - define `X-Forwarded-For` com o IP que ele viu, substituindo o do cliente, e `X-Forwarded-Proto`
//   e `X-Forwarded-Host`;
// - devolve status, corpo e cabeçalhos do Pitaco intactos (inclusive `Retry-After` e `Location`);
// - timeouts de 5 s para conectar e 10 s para a resposta.

import { Buffer } from 'node:buffer';
import http from 'node:http';
import https from 'node:https';

function readFlag(name) {
  const index = process.argv.indexOf(`--${name}`);
  return index === -1 ? undefined : process.argv[index + 1];
}

const PORT = Number(readFlag('port') ?? process.env.PITACO_PROXY_PORT ?? 8787);
const TARGET = new URL(readFlag('target') ?? process.env.PITACO_PROXY_TARGET ?? 'http://localhost:8080/api');
const PREFIX = (readFlag('prefix') ?? process.env.PITACO_PROXY_PREFIX ?? '/pitaco').replace(/\/+$/, '');
const TARGET_PATH = TARGET.pathname.replace(/\/+$/, '');
const CONNECT_TIMEOUT_MS = 5_000;
const RESPONSE_TIMEOUT_MS = 10_000;

// Cabeçalhos de salto não atravessam proxy nenhum. Os de origem saem porque só o proxy escreve a
// origem: um `CF-Connecting-IP` vindo do cliente seria lido pelo Pitaco (o proxy local está em
// loopback, que é confiável) e deixaria o cliente escolher a própria origem no limite por IP.
const DROPPED_REQUEST_HEADERS = new Set([
  'connection',
  'keep-alive',
  'proxy-connection',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
  'host',
  'forwarded',
  'x-forwarded-for',
  'x-forwarded-proto',
  'x-forwarded-host',
  'x-real-ip',
  'cf-connecting-ip',
]);
const DROPPED_RESPONSE_HEADERS = new Set(['connection', 'keep-alive', 'transfer-encoding', 'trailer', 'upgrade']);

function problem(response, status, code, detail) {
  const body = JSON.stringify({ type: 'about:blank', status, code, detail });
  response.writeHead(status, {
    'Content-Type': 'application/problem+json',
    'Content-Length': Buffer.byteLength(body),
  });
  response.end(body);
}

function clientIp(request) {
  const address = request.socket.remoteAddress ?? '';
  return address.startsWith('::ffff:') ? address.slice('::ffff:'.length) : address;
}

function upstreamPath(request) {
  const url = new URL(request.url ?? '/', 'http://proxy.local');
  const collectPrefix = `${PREFIX}/collect/`;
  if (!url.pathname.startsWith(collectPrefix)) return null;
  return `${TARGET_PATH}/collect/${url.pathname.slice(collectPrefix.length)}${url.search}`;
}

function forward(request, response, path) {
  const headers = {};
  for (const [name, value] of Object.entries(request.headers)) {
    if (!DROPPED_REQUEST_HEADERS.has(name) && value !== undefined) headers[name] = value;
  }
  headers['x-forwarded-for'] = clientIp(request);
  headers['x-forwarded-proto'] = request.socket.encrypted ? 'https' : 'http';
  if (request.headers.host) headers['x-forwarded-host'] = request.headers.host;

  const transport = TARGET.protocol === 'https:' ? https : http;
  const upstream = transport.request({
    protocol: TARGET.protocol,
    hostname: TARGET.hostname,
    port: TARGET.port || undefined,
    method: request.method,
    path,
    headers,
  });

  const connectTimer = setTimeout(() => upstream.destroy(new Error('connect_timeout')), CONNECT_TIMEOUT_MS);
  const onConnected = () => {
    clearTimeout(connectTimer);
    upstream.setTimeout(RESPONSE_TIMEOUT_MS, () => upstream.destroy(new Error('response_timeout')));
  };
  upstream.on('socket', (socket) => {
    // Socket reaproveitado pelo keep-alive já está conectado e não emite `connect` de novo.
    if (!socket.connecting) onConnected();
    else socket.once(TARGET.protocol === 'https:' ? 'secureConnect' : 'connect', onConnected);
  });

  upstream.on('response', (upstreamResponse) => {
    clearTimeout(connectTimer);
    const responseHeaders = {};
    for (const [name, value] of Object.entries(upstreamResponse.headers)) {
      if (!DROPPED_RESPONSE_HEADERS.has(name) && value !== undefined) responseHeaders[name] = value;
    }
    response.writeHead(upstreamResponse.statusCode ?? 502, upstreamResponse.statusMessage, responseHeaders);
    upstreamResponse.pipe(response);
    upstreamResponse.on('end', () => log(request, upstreamResponse.statusCode, upstreamResponse.headers));
  });

  upstream.on('error', (error) => {
    clearTimeout(connectTimer);
    const timedOut = error.message === 'connect_timeout' || error.message === 'response_timeout';
    log(request, timedOut ? 504 : 502, {}, error.message);
    if (response.headersSent) {
      response.destroy();
      return;
    }
    problem(
      response,
      timedOut ? 504 : 502,
      timedOut ? 'gateway.timeout' : 'gateway.upstream_unavailable',
      'O proxy local não conseguiu falar com o Pitaco',
    );
  });

  // O corpo passa como veio: nem reescrito, nem recomprimido, nem truncado.
  request.pipe(upstream);
}

const startedAt = new Map();

function log(request, status, headers, note) {
  const began = startedAt.get(request) ?? Date.now();
  startedAt.delete(request);
  const retryAfter = headers['retry-after'] ? ` Retry-After=${headers['retry-after']}` : '';
  const extra = note ? ` (${note})` : '';
  console.log(`${request.method} ${request.url} -> ${status} em ${Date.now() - began} ms${retryAfter}${extra}`);
}

const server = http.createServer((request, response) => {
  startedAt.set(request, Date.now());
  const path = upstreamPath(request);
  if (path === null) {
    log(request, 404, {}, 'fora da superfície pública, não repassado');
    problem(response, 404, 'gateway.route_not_found', 'O proxy só repassa {prefixo}/collect/*');
    return;
  }
  if (request.method !== 'POST') {
    log(request, 405, {}, 'só POST é repassado');
    response.setHeader('Allow', 'POST');
    problem(response, 405, 'gateway.method_not_allowed', 'O proxy só repassa POST');
    return;
  }
  forward(request, response, path);
});

server.listen(PORT, () => {
  console.log(`Proxy do Pitaco ouvindo em http://0.0.0.0:${PORT}${PREFIX}`);
  console.log(`Repassa ${PREFIX}/collect/* para ${TARGET.origin}${TARGET_PATH}/collect/*`);
  console.log('Ctrl+C para parar.');
});

function shutdown() {
  console.log('Parando o proxy.');
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1_000).unref();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
