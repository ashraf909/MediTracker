const FIREBASE_PROJECT_ID = 'meditracker-36f0c';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const MESSAGING_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
let cachedGoogleToken = null;

const base64Url = (value) => {
  const bytes = typeof value === 'string' ? new TextEncoder().encode(value) : new Uint8Array(value);
  let binary = '';
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
};

const importPrivateKey = async (pem) => {
  const encoded = pem
    .replace(/\\n/g, '\n')
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\s/g, '');
  const binary = atob(encoded);
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  return crypto.subtle.importKey(
    'pkcs8',
    bytes.buffer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
};

const getGoogleAccessToken = async (serviceAccount) => {
  const now = Math.floor(Date.now() / 1000);
  if (cachedGoogleToken?.expiresAt > now + 60) return cachedGoogleToken.value;

  const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claims = base64Url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: MESSAGING_SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${claims}`;
  const key = await importPrivateKey(serviceAccount.private_key);
  const signature = await crypto.subtle.sign(
    { name: 'RSASSA-PKCS1-v1_5' },
    key,
    new TextEncoder().encode(unsigned),
  );

  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: `${unsigned}.${base64Url(signature)}`,
    }),
  });
  if (!response.ok) throw new Error('GOOGLE_AUTH_FAILED');
  const result = await response.json();
  cachedGoogleToken = { value: result.access_token, expiresAt: now + Number(result.expires_in || 3600) };
  return cachedGoogleToken.value;
};

const decodeFirestoreValue = (value = {}) => {
  if ('stringValue' in value) return value.stringValue;
  if ('integerValue' in value) return Number(value.integerValue);
  if ('doubleValue' in value) return Number(value.doubleValue);
  if ('booleanValue' in value) return value.booleanValue;
  if ('timestampValue' in value) return value.timestampValue;
  if ('nullValue' in value) return null;
  if ('arrayValue' in value) return (value.arrayValue.values || []).map(decodeFirestoreValue);
  if ('mapValue' in value) {
    return Object.fromEntries(Object.entries(value.mapValue.fields || {})
      .map(([key, fieldValue]) => [key, decodeFirestoreValue(fieldValue)]));
  }
  return null;
};

const decodeFirebaseUid = (idToken) => {
  try {
    const payload = idToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload)).sub || null;
  } catch {
    return null;
  }
};

const jsonResponse = (body, status = 200, origin = '*') => new Response(JSON.stringify(body), {
  status,
  headers: {
    'content-type': 'application/json; charset=utf-8',
    'access-control-allow-origin': origin,
    'access-control-allow-headers': 'authorization, content-type',
    'access-control-allow-methods': 'POST, OPTIONS',
    vary: 'Origin',
  },
});

const preflightResponse = (origin = '*') => new Response(null, {
  status: 204,
  headers: {
    'access-control-allow-origin': origin,
    'access-control-allow-headers': 'authorization, content-type',
    'access-control-allow-methods': 'POST, OPTIONS',
    vary: 'Origin',
  },
});

const sendReminder = async (request, env) => {
  const origin = request.headers.get('origin') || '*';
  const authorization = request.headers.get('authorization') || '';
  const idToken = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';
  if (!idToken) return jsonResponse({ error: 'SIGN_IN_REQUIRED' }, 401, origin);

  let input;
  try {
    input = await request.json();
  } catch {
    return jsonResponse({ error: 'INVALID_REQUEST' }, 400, origin);
  }

  const familyCode = String(input.familyCode || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  const medicineId = String(input.medicineId || '');
  if (!/^[A-Z0-9]{8}$/.test(familyCode) || !medicineId) {
    return jsonResponse({ error: 'INVALID_REQUEST' }, 400, origin);
  }

  const projectId = env.FIREBASE_PROJECT_ID || FIREBASE_PROJECT_ID;
  const familyResponse = await fetch(
    `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/families/${familyCode}`,
    { headers: { authorization: `Bearer ${idToken}` } },
  );
  if (!familyResponse.ok) return jsonResponse({ error: 'FAMILY_ACCESS_DENIED' }, 403, origin);

  const document = await familyResponse.json();
  const family = Object.fromEntries(Object.entries(document.fields || {})
    .map(([key, value]) => [key, decodeFirestoreValue(value)]));
  const callerUid = decodeFirebaseUid(idToken);
  const isCaregiver = Array.isArray(family.caregivers)
    && family.caregivers.some((caregiver) => caregiver?.id === callerUid);
  if (!isCaregiver) return jsonResponse({ error: 'CAREGIVER_REQUIRED' }, 403, origin);

  const medicine = (family.medicines || []).find((item) => item?.id === medicineId);
  if (!medicine) return jsonResponse({ error: 'MEDICINE_NOT_FOUND' }, 404, origin);
  const tokens = [...new Set((family.patientPushTokens || []).filter(Boolean))];
  if (!tokens.length) return jsonResponse({ error: 'PATIENT_DEVICE_NOT_REGISTERED' }, 409, origin);

  let serviceAccount;
  try {
    serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  } catch {
    return jsonResponse({ error: 'PUSH_SERVER_NOT_CONFIGURED' }, 503, origin);
  }
  const accessToken = await getGoogleAccessToken(serviceAccount);
  const alertId = String(input.alertId || `alert_${Date.now()}`);
  const title = `MediTracker · ${medicine.banglaName || medicine.name || 'Medicine reminder'}`;
  const body = `${medicine.name || 'Medicine'} · ${medicine.timing || 'Now'}`;

  const results = await Promise.all(tokens.map(async (token) => {
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: 'POST',
      headers: {
        authorization: `Bearer ${accessToken}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        message: {
          token,
          data: {
            type: 'caregiver_reminder',
            familyCode,
            medicineId,
            alertId,
            title,
            body,
          },
          android: { priority: 'HIGH', ttl: '120s' },
        },
      }),
    });
    return response.ok;
  }));

  const sent = results.filter(Boolean).length;
  if (!sent) return jsonResponse({ error: 'PUSH_DELIVERY_FAILED' }, 502, origin);
  return jsonResponse({ ok: true, sent }, 200, origin);
};

const sendScheduleSync = async (request, env) => {
  const origin = request.headers.get('origin') || '*';
  const authorization = request.headers.get('authorization') || '';
  const idToken = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';
  if (!idToken) return jsonResponse({ error: 'SIGN_IN_REQUIRED' }, 401, origin);

  let input;
  try {
    input = await request.json();
  } catch {
    return jsonResponse({ error: 'INVALID_REQUEST' }, 400, origin);
  }

  const familyCode = String(input.familyCode || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (!/^[A-Z0-9]{8}$/.test(familyCode)) {
    return jsonResponse({ error: 'INVALID_REQUEST' }, 400, origin);
  }

  const projectId = env.FIREBASE_PROJECT_ID || FIREBASE_PROJECT_ID;
  const familyResponse = await fetch(
    `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/families/${familyCode}`,
    { headers: { authorization: `Bearer ${idToken}` } },
  );
  if (!familyResponse.ok) return jsonResponse({ error: 'FAMILY_ACCESS_DENIED' }, 403, origin);

  const document = await familyResponse.json();
  const family = Object.fromEntries(Object.entries(document.fields || {})
    .map(([key, value]) => [key, decodeFirestoreValue(value)]));
  const callerUid = decodeFirebaseUid(idToken);
  const isCaregiver = Array.isArray(family.caregivers)
    && family.caregivers.some((caregiver) => caregiver?.id === callerUid);
  if (!isCaregiver) return jsonResponse({ error: 'CAREGIVER_REQUIRED' }, 403, origin);

  const tokens = [...new Set((family.patientPushTokens || []).filter(Boolean))];
  if (!tokens.length) return jsonResponse({ ok: true, sent: 0 }, 200, origin);

  let serviceAccount;
  try {
    serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  } catch {
    return jsonResponse({ error: 'PUSH_SERVER_NOT_CONFIGURED' }, 503, origin);
  }
  const accessToken = await getGoogleAccessToken(serviceAccount);
  const results = await Promise.all(tokens.map(async (token) => {
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: 'POST',
      headers: {
        authorization: `Bearer ${accessToken}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        message: {
          token,
          data: { type: 'schedule_updated', familyCode },
          android: { priority: 'HIGH', ttl: '3600s' },
        },
      }),
    });
    return response.ok;
  }));

  const sent = results.filter(Boolean).length;
  if (!sent) return jsonResponse({ error: 'PUSH_DELIVERY_FAILED' }, 502, origin);
  return jsonResponse({ ok: true, sent }, 200, origin);
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/api/reminders/send') {
      if (request.method === 'OPTIONS') return preflightResponse(request.headers.get('origin') || '*');
      if (request.method !== 'POST') return jsonResponse({ error: 'METHOD_NOT_ALLOWED' }, 405);
      try {
        return await sendReminder(request, env);
      } catch (error) {
        console.error('Reminder delivery failed.', error);
        return jsonResponse({ error: 'PUSH_SERVER_ERROR' }, 500, request.headers.get('origin') || '*');
      }
    }

    if (url.pathname === '/api/schedules/sync') {
      if (request.method === 'OPTIONS') return preflightResponse(request.headers.get('origin') || '*');
      if (request.method !== 'POST') return jsonResponse({ error: 'METHOD_NOT_ALLOWED' }, 405);
      try {
        return await sendScheduleSync(request, env);
      } catch (error) {
        console.error('Schedule synchronization failed.', error);
        return jsonResponse({ error: 'PUSH_SERVER_ERROR' }, 500, request.headers.get('origin') || '*');
      }
    }

    const response = await env.ASSETS.fetch(request);
    if (response.status !== 404 || request.method !== 'GET') return response;
    return env.ASSETS.fetch(new Request(new URL('/index.html', request.url), request));
  },
};
