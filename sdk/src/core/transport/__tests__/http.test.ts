import { jestClock } from '../../../__tests__/support/fakeClock';
import { respond } from '../../../__tests__/support/fakeServer';
import { systemTimers } from '../../timers';
import { type FetchLike, HttpClient, parseRetryAfter, type RequestInitLike } from '../http';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

function client(fetch: FetchLike | undefined, timeoutMs = 10_000) {
  return new HttpClient({
    baseUrl: 'https://pitaco.test/api',
    apiKey: 'pk_test',
    sdkVersion: '1.0.0',
    fetch,
    clock: jestClock,
    timers: systemTimers,
    timeoutMs,
  });
}

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('cliente HTTP', () => {
  it('manda a chave, a versão do SDK e JSON em toda requisição', async () => {
    const calls: { url: string; init: RequestInitLike }[] = [];
    const fetch: FetchLike = (url, init) => {
      calls.push({ url, init });
      return Promise.resolve(respond({ status: 200, body: { survey: null } }));
    };
    const outcome = await client(fetch).post('/collect/eligibility', { event: 'x' }, { expectJson: true });
    expect(outcome).toEqual({ kind: 'success', status: 200, body: { survey: null } });
    expect(calls[0]?.url).toBe('https://pitaco.test/api/collect/eligibility');
    expect(calls[0]?.init.method).toBe('POST');
    expect(calls[0]?.init.headers).toMatchObject({
      'X-Pitaco-Key': 'pk_test',
      'X-Pitaco-Sdk-Version': '1.0.0',
      'Content-Type': 'application/json',
    });
    expect(JSON.parse(calls[0]!.init.body)).toEqual({ event: 'x' });
  });

  it('2xx sem corpo esperado não lê o corpo', async () => {
    const text = jest.fn(() => Promise.resolve(''));
    const outcome = await client(() => Promise.resolve({ status: 204, text })).post('/collect/x', {});
    expect(outcome).toEqual({ kind: 'success', status: 204, body: null });
    expect(text).not.toHaveBeenCalled();
  });

  it('2xx com corpo que não é JSON é malformado', async () => {
    const outcome = await client(() => Promise.resolve(respond({ status: 200, raw: '<html>gateway</html>' }))).post(
      '/collect/eligibility',
      {},
      { expectJson: true },
    );
    expect(outcome).toEqual({ kind: 'malformed', status: 200 });
  });

  it('rede fora vira "tentar de novo", sem exceção', async () => {
    const outcome = await client(() => Promise.reject(new TypeError('Network request failed'))).post('/collect/x', {});
    expect(outcome).toEqual({ kind: 'retry', reason: 'network' });
    expect(await client(undefined).post('/collect/x', {})).toEqual({ kind: 'retry', reason: 'network' });
  });

  it('API lenta: abandona no timeout e aborta a conexão', async () => {
    let signal: { aborted?: boolean } | undefined;
    const fetch: FetchLike = (_url, init) => {
      signal = init.signal as { aborted?: boolean };
      return new Promise(() => undefined);
    };
    let settled = false;
    const pending = client(fetch).post('/collect/eligibility', {}, { timeoutMs: 3000 });
    void pending.then(() => {
      settled = true;
    });
    await jest.advanceTimersByTimeAsync(2999);
    expect(settled).toBe(false);
    await jest.advanceTimersByTimeAsync(1);
    await expect(pending).resolves.toEqual({ kind: 'retry', reason: 'timeout' });
    expect(signal?.aborted).toBe(true);
  });

  it('5xx vira "tentar de novo"', async () => {
    const outcome = await client(() => Promise.resolve(respond({ status: 503 }))).post('/collect/x', {});
    expect(outcome).toEqual({ kind: 'retry', reason: 'server', status: 503 });
  });

  it('401 e 403 são chave inválida, com o code do problema', async () => {
    const problem = { status: 401, code: 'api_key.invalid' };
    expect(await client(() => Promise.resolve(respond({ status: 401, body: problem }))).post('/collect/x', {})).toEqual({
      kind: 'unauthorized',
      status: 401,
      code: 'api_key.invalid',
    });
    expect(await client(() => Promise.resolve(respond({ status: 403, raw: 'proibido' }))).post('/collect/x', {})).toEqual({
      kind: 'unauthorized',
      status: 403,
      code: null,
    });
  });

  it('outros 4xx são recusa, com o code', async () => {
    const outcome = await client(() =>
      Promise.resolve(respond({ status: 422, body: { code: 'submission.rejected' } })),
    ).post('/collect/x', {});
    expect(outcome).toMatchObject({ kind: 'rejected', status: 422, code: 'submission.rejected' });
  });

  it('429 respeita o Retry-After: nada sai antes do prazo', async () => {
    const fetch = jest.fn<ReturnType<FetchLike>, Parameters<FetchLike>>(() =>
      Promise.resolve(respond({ status: 429, headers: { 'Retry-After': '7' } })),
    );
    const http = client(fetch);
    expect(await http.post('/collect/x', {})).toEqual({ kind: 'rate_limited', retryAfterMs: 7000 });
    expect(await http.post('/collect/x', {})).toEqual({ kind: 'blocked', until: NOW + 7000 });
    expect(fetch).toHaveBeenCalledTimes(1);
    jest.advanceTimersByTime(7000);
    fetch.mockImplementation(() => Promise.resolve(respond({ status: 204 })));
    expect(await http.post('/collect/x', {})).toMatchObject({ kind: 'success' });
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('interpreta Retry-After em segundos e em data HTTP, com piso e teto', () => {
    expect(parseRetryAfter('30', NOW)).toBe(30_000);
    expect(parseRetryAfter(new Date(NOW + 90_000).toUTCString(), NOW)).toBe(90_000);
    expect(parseRetryAfter('0', NOW)).toBe(1000);
    expect(parseRetryAfter('999999', NOW)).toBe(60 * 60 * 1000);
    expect(parseRetryAfter(null, NOW)).toBe(30_000);
    expect(parseRetryAfter('amanhã', NOW)).toBe(30_000);
  });
});
