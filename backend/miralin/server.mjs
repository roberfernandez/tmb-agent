import http from 'node:http';
const UPSTREAM = 'https://miralin.cat/socket.io/';
const ORIGIN = 'https://roberfernandez.github.io';
let snapshot = {}, receivedAt = 0, connected = false, running = false, lastUse = Date.now(), stopping = false;
let controller;
const pause = ms => new Promise(r => setTimeout(r, ms));
async function request(params, body, signal) {
  const response = await fetch(UPSTREAM + '?' + new URLSearchParams(params), {
    method: body === undefined ? 'GET' : 'POST',
    headers: body === undefined ? {} : { 'Content-Type': 'text/plain;charset=UTF-8' },
    body, signal: AbortSignal.any([signal, AbortSignal.timeout(40000)])
  });
  if (!response.ok) throw new Error('Upstream HTTP ' + response.status);
  return response.text();
}
function start() {
  if (running || stopping) return;
  running = true;
  void (async () => {
    let retry = 2000;
    while (!stopping && Date.now() - lastUse < 120000) {
      controller = new AbortController();
      const signal = controller.signal;
      let params;
      try {
        const hello = await request({EIO:'4',transport:'polling'}, undefined, signal);
        if (!hello.startsWith('0')) throw new Error('Invalid handshake');
        const {sid} = JSON.parse(hello.slice(1));
        if (!sid) throw new Error('Missing session');
        params = {EIO:'4',transport:'polling',sid};
        await request(params, '40', signal);
        while (!stopping && Date.now() - lastUse < 120000) {
          const packets = (await request(params, undefined, signal)).split('\x1e');
          for (const packet of packets) {
            if (packet === '2') await request(params, '3', signal);
            else if (packet.startsWith('40')) connected = true;
            else if (packet === '1' || packet.startsWith('41')) throw new Error('Upstream closed');
            else if (packet.startsWith('42')) {
              const [event, data] = JSON.parse(packet.slice(2));
              if (event === 'mapUpdate' && data?.L4?.trains) {
                snapshot = data.L4.trains;
                receivedAt = Date.now();
                connected = true;
                retry = 2000;
              }
            }
          }
        }
      } catch (e) {
        connected = false;
        if (!stopping) console.warn('Connection interrupted:', e.message);
      } finally {
        if (params && !signal.aborted) {
          try { await request(params, '41', signal); } catch {}
        }
        connected = false;
      }
      if (!stopping && Date.now() - lastUse < 120000) {
        await pause(retry); retry = Math.min(retry * 2, 30000);
      }
    }
    running = false;
  })();
}
const server = http.createServer((req, res) => {
  const origin = req.headers.origin;
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Vary', 'Origin');
  if (origin && origin !== ORIGIN) {
    res.writeHead(403, {'Content-Type':'application/json'});
    return res.end(JSON.stringify({error:'Origin not allowed'}));
  }
  if (origin) res.setHeader('Access-Control-Allow-Origin', ORIGIN);
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }
  if (req.method !== 'GET') { res.writeHead(405); return res.end(); }
  const url = new URL(req.url, 'http://localhost');
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  if (url.pathname === '/health') return res.end(JSON.stringify({ok:true, connected}));
  if (!['/', '/api/trains'].includes(url.pathname)) { res.writeHead(404); return res.end(JSON.stringify({error:'Not found'})); }
  lastUse = Date.now(); start();
  const ageSeconds = receivedAt ? Math.floor((Date.now()-receivedAt)/1000) : null;
  const stale = !connected || ageSeconds === null || ageSeconds > 45;
  const ids = (url.searchParams.get('ids') || '403,417').split(',');
  if (ids.length > 30 || ids.some(id => !/^4\d{2}$/.test(id))) {
    res.writeHead(400); return res.end(JSON.stringify({error:'Invalid L4 train IDs'}));
  }
  const trains = Object.fromEntries(ids.map(id => {
    const t = snapshot[id];
    return [id,t ? {id:String(t.id),station:t.estacion,track:t.track,platformTrack:t.platformTrack,
      destination:t.destiLabel,secondsLeft:t.secsLeft,status:t.status,
      waitingDeparture:!!t.isWaitingDeparture,turnaround:!!t.isTurnaround} : null];
  }));
  res.writeHead(stale ? 503 : 200);
  res.end(JSON.stringify({source:'Miralin.cat',connected,stale,receivedAt:receivedAt?new Date(receivedAt).toISOString():null,
    ageSeconds, note:'Reception time is not the source measurement time. secondsLeft is not necessarily time to your relief.',trains}));
});
server.listen(Number(process.env.PORT || 10000), '0.0.0.0', () => console.log('Service listening'));
function shutdown() { stopping=true; controller?.abort(); server.close(); setTimeout(()=>process.exit(0),1500).unref(); }
process.on('SIGTERM',shutdown); process.on('SIGINT',shutdown);
