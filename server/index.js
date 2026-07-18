import http from 'node:http';
import { URL } from 'node:url';

const PORT = Number(process.env.PORT || 10000);
const DEFAULT_LIMIT = 160;
const MAX_LIMIT = 500;
const MAX_STORED = 2000;
const INGEST_API_KEY = process.env.MNM_INGEST_API_KEY || '';
const SUPPORTED_TIMEFRAMES = new Map([
  ['M15', 15 * 60_000],
  ['H1', 60 * 60_000],
  ['H4', 4 * 60 * 60_000],
  ['D1', 24 * 60 * 60_000]
]);
const liveStore = new Map();

function json(response, status, payload) {
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'access-control-allow-origin': '*',
    'access-control-allow-headers': 'content-type,x-api-key',
    'access-control-allow-methods': 'GET,POST,OPTIONS'
  });
  response.end(JSON.stringify(payload));
}

function clampLimit(value) {
  const parsed = Number.parseInt(value || String(DEFAULT_LIMIT), 10);
  if (!Number.isFinite(parsed)) return DEFAULT_LIMIT;
  return Math.max(90, Math.min(MAX_LIMIT, parsed));
}

function normaliseSymbol(symbol) {
  return String(symbol || 'XAUUSD').trim().toUpperCase().replace('/', '');
}

function streamKey(symbol, timeframe) {
  return `${normaliseSymbol(symbol)}:${timeframe.toUpperCase()}`;
}

function validCandle(candle) {
  const values = ['time', 'open', 'high', 'low', 'close'].map((key) => Number(candle?.[key]));
  if (values.some((value) => !Number.isFinite(value))) return false;
  const [, open, high, low, close] = values;
  return high >= Math.max(open, close) && low <= Math.min(open, close) && low <= high;
}

function syntheticCandles(timeframe, limit) {
  const step = SUPPORTED_TIMEFRAMES.get(timeframe);
  const now = Date.now();
  const alignedNow = Math.floor(now / step) * step;
  const seed = [...timeframe].reduce((total, char) => total + char.charCodeAt(0), 0);
  let price = 3325 + seed * 0.05;

  return Array.from({ length: limit }, (_, index) => {
    const time = alignedNow - (limit - 1 - index) * step;
    const wave = Math.sin((index + seed) / 7) * 2.8;
    const drift = timeframe === 'M15' ? 0.08 : timeframe === 'H1' ? 0.16 : timeframe === 'H4' ? 0.28 : 0.42;
    const open = price;
    const close = open + drift + wave * 0.08;
    const high = Math.max(open, close) + 1.1 + Math.abs(Math.sin(index));
    const low = Math.min(open, close) - 1.0 - Math.abs(Math.cos(index));
    price = close;
    return { time, open, high, low, close };
  });
}

function previousDayFrom(candles) {
  const reference = candles.slice(-25, -1);
  return {
    high: Math.max(...reference.map((candle) => candle.high)),
    low: Math.min(...reference.map((candle) => candle.low))
  };
}

async function readJsonBody(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 2_000_000) throw new Error('payload_too_large');
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
}

export function createServer() {
  return http.createServer(async (request, response) => {
    const url = new URL(request.url || '/', `http://${request.headers.host || 'localhost'}`);

    if (request.method === 'OPTIONS') return json(response, 204, {});

    if (request.method === 'GET' && url.pathname === '/') {
      return json(response, 200, {
        service: 'mnm-au-seekers-api',
        status: 'online',
        routes: ['/health', '/market-data', '/sample-market-data', '/ingest']
      });
    }

    if (request.method === 'GET' && url.pathname === '/health') {
      const liveCandles = [...liveStore.values()].reduce((total, candles) => total + candles.length, 0);
      return json(response, 200, {
        status: 'ok',
        service: 'mnm-au-seekers-api',
        version: '0.2.0',
        marketDataMode: liveCandles >= 90 ? 'live-ingested' : 'synthetic-development',
        liveStreams: liveStore.size,
        liveCandles,
        updatedAt: Date.now()
      });
    }

    if (request.method === 'POST' && url.pathname === '/ingest') {
      if (INGEST_API_KEY && request.headers['x-api-key'] !== INGEST_API_KEY) {
        return json(response, 401, { error: 'invalid_api_key' });
      }
      try {
        const body = await readJsonBody(request);
        const symbol = normaliseSymbol(body.symbol);
        const timeframe = String(body.timeframe || '').toUpperCase();
        if (!SUPPORTED_TIMEFRAMES.has(timeframe)) {
          return json(response, 400, { error: 'unsupported_timeframe', supported: [...SUPPORTED_TIMEFRAMES.keys()] });
        }
        if (!Array.isArray(body.candles) || body.candles.length === 0 || body.candles.some((candle) => !validCandle(candle))) {
          return json(response, 400, { error: 'invalid_candles' });
        }
        const key = streamKey(symbol, timeframe);
        const merged = new Map((liveStore.get(key) || []).map((candle) => [Number(candle.time), candle]));
        for (const candle of body.candles) {
          merged.set(Number(candle.time), {
            time: Number(candle.time),
            open: Number(candle.open),
            high: Number(candle.high),
            low: Number(candle.low),
            close: Number(candle.close)
          });
        }
        const ordered = [...merged.values()].sort((a, b) => a.time - b.time).slice(-MAX_STORED);
        liveStore.set(key, ordered);
        return json(response, 200, {
          accepted: body.candles.length,
          stored: ordered.length,
          symbol,
          timeframe,
          updatedAt: ordered.at(-1).time
        });
      } catch (error) {
        return json(response, 400, {
          error: error.message === 'payload_too_large' ? 'payload_too_large' : 'invalid_json'
        });
      }
    }

    if (request.method === 'GET' && url.pathname === '/market-data') {
      const symbol = normaliseSymbol(url.searchParams.get('symbol') || 'XAUUSD');
      const timeframe = (url.searchParams.get('timeframe') || 'H4').toUpperCase();
      const limit = clampLimit(url.searchParams.get('limit'));
      if (!SUPPORTED_TIMEFRAMES.has(timeframe)) {
        return json(response, 400, { error: 'unsupported_timeframe', supported: [...SUPPORTED_TIMEFRAMES.keys()] });
      }

      const live = (liveStore.get(streamKey(symbol, timeframe)) || []).slice(-limit);
      if (live.length < 90) {
        return json(response, 503, {
          error: 'insufficient_live_history',
          message: 'At least 90 ingested candles are required before the feed is marked live.',
          symbol,
          timeframe,
          available: live.length,
          required: 90,
          sampleRoute: `/sample-market-data?symbol=${symbol}&timeframe=${timeframe}&limit=${limit}`
        });
      }

      return json(response, 200, {
        symbol,
        timeframe,
        source: 'mt5-ingested',
        isLiveBrokerData: true,
        updatedAt: live.at(-1).time,
        previousDay: previousDayFrom(live),
        candles: live
      });
    }

    if (request.method === 'GET' && url.pathname === '/sample-market-data') {
      const symbol = normaliseSymbol(url.searchParams.get('symbol') || 'XAUUSD');
      const timeframe = (url.searchParams.get('timeframe') || 'H4').toUpperCase();
      const limit = clampLimit(url.searchParams.get('limit'));
      if (!SUPPORTED_TIMEFRAMES.has(timeframe)) {
        return json(response, 400, { error: 'unsupported_timeframe', supported: [...SUPPORTED_TIMEFRAMES.keys()] });
      }
      const candles = syntheticCandles(timeframe, limit);
      return json(response, 200, {
        symbol,
        timeframe,
        source: 'synthetic-development',
        isLiveBrokerData: false,
        updatedAt: candles.at(-1).time,
        previousDay: previousDayFrom(candles),
        candles
      });
    }

    return json(response, 404, {
      error: 'not_found',
      routes: ['/health', '/market-data', '/sample-market-data', '/ingest']
    });
  });
}

if (process.env.NODE_ENV !== 'test') {
  createServer().listen(PORT, '0.0.0.0', () => {
    console.log(`MNM AU Seekers API listening on port ${PORT}`);
  });
}
