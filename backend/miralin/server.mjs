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

let webpush=null,vapid=null;
try {
 webpush=(await import('web-push')).default;
 vapid=webpush.generateVAPIDKeys();
 webpush.setVapidDetails('https://roberfernandez.github.io/tmb-agent/',vapid.publicKey,vapid.privateKey);
} catch { console.warn('Push test unavailable: install dependencies with npm install'); }
const pendingPush=new Map();
async function pushRoute(req,res,url){
 if(url.pathname==='/api/push-key'&&req.method==='GET'){
  res.writeHead(vapid?200:503);res.end(JSON.stringify(vapid?{publicKey:vapid.publicKey}:{error:'Cal instal·lar dependències a Render: npm install'}));return;
 }
 if(url.pathname!=='/api/push-test'||req.method!=='POST'){res.writeHead(404);res.end('{}');return}
 if(!webpush){res.writeHead(503);res.end(JSON.stringify({error:'Push no disponible. Revisa el Build Command.'}));return}
 try{
  let body='';for await(const chunk of req){body+=chunk;if(body.length>8192)throw Error('Peticio massa gran')}
  const {subscription}=JSON.parse(body);
  const endpoint=new URL(subscription?.endpoint);
  const allowed=endpoint.hostname==='fcm.googleapis.com'||endpoint.hostname==='updates.push.services.mozilla.com'||endpoint.hostname.endsWith('.push.apple.com')||endpoint.hostname==='web.push.apple.com';
  if(endpoint.protocol!=='https:'||endpoint.port||endpoint.username||endpoint.password||!allowed||!subscription?.keys?.p256dh||!subscription?.keys?.auth)throw Error('Subscripció push no vàlida');
  if(pendingPush.size>=10||pendingPush.has(endpoint.href))throw Error('Ja hi ha una prova pendent. Espera un minut.');
  const timer=setTimeout(async()=>{
   try{
    const t=snapshot['403'],fresh=connected&&receivedAt&&Date.now()-receivedAt<45000;
    const body=fresh&&t?'Prova en segon pla · 403: '+t.estacion+' · via '+t.track:'Prova en segon pla rebuda. Seguiment inactiu: sense dades recents del tren.';
    await webpush.sendNotification(subscription,JSON.stringify({title:'El meu servei · Prova push',body,url:'/tmb-agent/proves/servei/'}),{TTL:120,urgency:'high',timeout:15000});
    console.log('Push test accepted by delivery service');
   }catch(e){console.warn('Push test failed:',e.statusCode||e.message)}
   finally{pendingPush.delete(endpoint.href)}
  },60000);
  pendingPush.set(endpoint.href,timer);lastUse=Date.now();start();
  res.writeHead(202);res.end(JSON.stringify({scheduledAt:new Date(Date.now()+60000).toISOString(),temporary:true}));
 }catch(e){res.writeHead(400);res.end(JSON.stringify({error:e.message}))}
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
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  if (req.method === 'OPTIONS') { res.setHeader('Access-Control-Allow-Headers','Content-Type'); res.writeHead(204); return res.end(); }
  res.setHeader('Access-Control-Allow-Headers','Content-Type');
  const pushUrl=new URL(req.url,'http://localhost');
  if(pushUrl.pathname.startsWith('/api/push-')){
    res.setHeader('Content-Type','application/json; charset=utf-8');
    if(req.method==='POST'&&origin!==ORIGIN){res.writeHead(403);return res.end('{}')}
    void pushRoute(req,res,pushUrl);return;
  }
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
