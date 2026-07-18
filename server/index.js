import http from 'node:http';
import { URL } from 'node:url';

const PORT = Number(process.env.PORT || 10000);
const DEFAULT_LIMIT = 160;
const MAX_LIMIT = 500;
const SUPPORTED_TIMEFRAMES = new Map([
  ['M15', 15 * 60_000],
  ['H1', 60 * 60_000],
  ['H4', 4 * 60 * 60_000],
  ['D1', 24 * 60 * 60_000]
]);

function json(response, status, payload) {
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'access-control-allow-origin': '*'
  });
  response.end(JSON.stringify(payload));
}

function clampLimit(value) {
  const parsed = Number.parseInt(value || String(DEFAULT_LIMIT), 10);
  if (!Number.isFinite(parsed)) return DEFAULT_LIMIT;
  return Math.max(90, Math.min(MAX_LIMIT, parsed));
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

function marketRequest(url) {
  const symbol = (url.searchParams.get('symbol') || 'XAUUSD').toUpperCase();
  const timeframe = (url.searchParams.get('timeframe') || 'H4').toUpperCase();
  const limit = clampLimit(url.searchParams.get('limit'));
  return { symbol, timeframe, limit };
}

function validateTimeframe(response, timeframe) {
  if (SUPPORTED_TIMEFRAMES.has(timeframe)) return true;
  json(response, 400, {
    error: 'unsupported_timeframe',
    supported: [...SUPPORTED_TIMEFRAMES.keys()]
  });
  return false;
}

export function createServer() {
  return http.createServer((request, response) => {
    const url = new URL(request.url || '/', `http://${request.headers.host || 'localhost'}`);
    const marketDataMode = process.env.MARKET_DATA_MODE || 'synthetic-development';

    if (request.method === 'GET' && url.pathname === '/health') {
      return json(response, 200, {
        status: 'ok',
        service: 'mnm-au-seekers-api',
        version: '0.1.0',
        marketDataMode,
        liveFeedConfigured: marketDataMode === 'live',
        updatedAt: Date.now()
      });
    }

    if (request.method === 'GET' && url.pathname === '/market-data') {
      const { symbol, timeframe } = marketRequest(url);
      if (!validateTimeframe(response, timeframe)) return;

      if (marketDataMode !== 'live') {
        return json(response, 503, {
          error: 'live_feed_not_configured',
          message: 'A licensed upstream market-data provider has not been configured.',
          symbol,
          timeframe,
          sampleRoute: `/sample-market-data?symbol=${symbol}&timeframe=${timeframe}&limit=${DEFAULT_LIMIT}`
        });
      }

      return json(response, 501, {
        error: 'live_adapter_not_implemented',
        message: 'Configure the licensed upstream adapter before enabling MARKET_DATA_MODE=live.'
      });
    }

    if (request.method === 'GET' && url.pathname === '/sample-market-data') {
      const { symbol, timeframe, limit } = marketRequest(url);
      if (!validateTimeframe(response, timeframe)) return;
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
      routes: [
        '/health',
        '/market-data?symbol=XAUUSD&timeframe=H4&limit=160',
        '/sample-market-data?symbol=XAUUSD&timeframe=H4&limit=160'
      ]
    });
  });
}

if (process.env.NODE_ENV !== 'test') {
  createServer().listen(PORT, '0.0.0.0', () => {
    console.log(`MNM AU Seekers API listening on port ${PORT}`);
  });
}
