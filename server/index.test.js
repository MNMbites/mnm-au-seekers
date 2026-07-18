import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer } from './index.js';

let server;
let baseUrl;

before(async () => {
  process.env.NODE_ENV = 'test';
  process.env.MARKET_DATA_MODE = 'synthetic-development';
  server = createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  baseUrl = `http://127.0.0.1:${address.port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
});

test('health reports that live feed is not configured', async () => {
  const response = await fetch(`${baseUrl}/health`);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.status, 'ok');
  assert.equal(body.liveFeedConfigured, false);
});

test('market-data refuses to label development candles as live', async () => {
  const response = await fetch(`${baseUrl}/market-data?symbol=XAUUSD&timeframe=H4`);
  assert.equal(response.status, 503);
  const body = await response.json();
  assert.equal(body.error, 'live_feed_not_configured');
});

test('sample route returns Android-compatible OHLC contract', async () => {
  const response = await fetch(`${baseUrl}/sample-market-data?symbol=XAUUSD&timeframe=H1&limit=100`);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.symbol, 'XAUUSD');
  assert.equal(body.timeframe, 'H1');
  assert.equal(body.isLiveBrokerData, false);
  assert.equal(body.candles.length, 100);
  assert.ok(body.candles.every((candle) => candle.high >= Math.max(candle.open, candle.close)));
  assert.ok(body.candles.every((candle) => candle.low <= Math.min(candle.open, candle.close)));
  assert.ok(body.previousDay.high > body.previousDay.low);
});

test('unsupported timeframe returns a clear contract error', async () => {
  const response = await fetch(`${baseUrl}/sample-market-data?timeframe=M5`);
  assert.equal(response.status, 400);
  const body = await response.json();
  assert.equal(body.error, 'unsupported_timeframe');
  assert.deepEqual(body.supported, ['M15', 'H1', 'H4', 'D1']);
});
