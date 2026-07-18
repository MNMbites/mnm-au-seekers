import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer } from './index.js';

let server;
let baseUrl;

before(async () => {
  process.env.NODE_ENV = 'test';
  server = createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  baseUrl = `http://127.0.0.1:${address.port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
});

function candles(count = 90) {
  let price = 3300;
  return Array.from({ length: count }, (_, index) => {
    const open = price;
    const close = open + 0.5;
    const candle = {
      time: 1_700_000_000_000 + index * 60_000,
      open,
      high: close + 0.4,
      low: open - 0.4,
      close
    };
    price = close;
    return candle;
  });
}

test('market-data refuses to claim live before history is ingested', async () => {
  const response = await fetch(`${baseUrl}/market-data?symbol=XAUUSD&timeframe=H4&limit=160`);
  assert.equal(response.status, 503);
  const body = await response.json();
  assert.equal(body.error, 'insufficient_live_history');
  assert.equal(body.required, 90);
});

test('valid ingest unlocks truthful live market-data response', async () => {
  const ingest = await fetch(`${baseUrl}/ingest`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ symbol: 'XAUUSD', timeframe: 'H4', candles: candles(90) })
  });
  assert.equal(ingest.status, 200);

  const response = await fetch(`${baseUrl}/market-data?symbol=XAUUSD&timeframe=H4&limit=160`);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.isLiveBrokerData, true);
  assert.equal(body.source, 'mt5-ingested');
  assert.equal(body.candles.length, 90);
  assert.ok(body.previousDay.high > body.previousDay.low);
});

test('sample endpoint remains explicitly synthetic', async () => {
  const response = await fetch(`${baseUrl}/sample-market-data?symbol=XAUUSD&timeframe=M15&limit=90`);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.isLiveBrokerData, false);
  assert.equal(body.source, 'synthetic-development');
  assert.equal(body.candles.length, 90);
});

test('unsupported timeframe returns a clear contract error', async () => {
  const response = await fetch(`${baseUrl}/sample-market-data?timeframe=M5`);
  assert.equal(response.status, 400);
  const body = await response.json();
  assert.equal(body.error, 'unsupported_timeframe');
  assert.deepEqual(body.supported, ['M15', 'H1', 'H4', 'D1']);
});
