import http from 'node:http';

const port = Number(process.env.MOCK_GEMINI_PORT || 9090);
const delayMs = Number(process.env.MOCK_GEMINI_DELAY_MS || 200);
const statusCode = Number(process.env.MOCK_GEMINI_STATUS || 200);
const responseText = process.env.MOCK_GEMINI_TEXT || 'Mock Gemini summary for local load testing.';

function readBody(req) {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
    });
    req.on('end', () => resolve(body));
  });
}

function json(res, status, payload) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(payload));
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    json(res, 200, { status: 'ok', delayMs, statusCode });
    return;
  }

  if (req.method !== 'POST' || !req.url.startsWith('/v1beta/models/')) {
    json(res, 404, { error: { message: 'Not found' } });
    return;
  }

  await readBody(req);

  setTimeout(() => {
    if (statusCode >= 400) {
      json(res, statusCode, {
        error: {
          code: statusCode,
          message: 'Mock Gemini failure',
          status: 'MOCK_FAILURE',
        },
      });
      return;
    }

    json(res, statusCode, {
      candidates: [
        {
          content: {
            parts: [
              {
                text: responseText,
              },
            ],
          },
        },
      ],
    });
  }, delayMs);
});

server.listen(port, () => {
  console.log(`[MockGemini] listening on http://localhost:${port} delayMs=${delayMs} status=${statusCode}`);
});
