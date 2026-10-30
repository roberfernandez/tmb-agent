import http from 'node:http';
const UPSTREAM = 'https://miralin.cat/socket.io/';
const ORIGIN = 'https://roberfernandez.github.io';
let snapshot = {}, receivedAt = 0, connected = false, running = false, lastUse = Date.now(), stopping = false;
let controller;
const PUSH_TEST_MS = 5 * 60 * 1000;
let testWatchUntil = 0;
const stationWatches=new Map();
let previousMap=null,previousMapAt=0;
function stationPasses(trains,now=Date.now()) {
 const previous=previousMap;const fresh=previous&&now-previousMapAt<45000;
 previousMap=trains;previousMapAt=now;
 for(const [endpoint,w] of stationWatches){
  if(now>=w.until){stationWatches.delete(endpoint);continue}
  if(!fresh)continue;
  for(const [id,t] of Object.entries(trains)){
   const old=previous[id];if(!old)continue;
   const via=Number(old.platformTrack??old.track);
   const atSelva=x=>String(x?.estacion||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase().includes("selva de mar");
   const next=String(t.estacion||"").toLowerCase();
   const passed=atSelva(old)&&!atSelva(t)&&Number(t.platformTrack??t.track)===via&&((via===1&&next.includes("poblenou"))||(via===2&&(next.includes("maresme")||next.includes("forum")||next.includes("fòrum"))));
   if(!passed)continue;
   const key=id+":"+via;if(now-(w.sent.get(key)||0)<120000)continue;
   w.sent.set(key,now);
   void webpush.sendNotification(w.subscription,JSON.stringify({title:"Selva de Mar · Tren "+id+" · via "+via,body:"Pas detectat segons el canvi d’estació de Miralin. Recepció "+new Date(now).toLocaleTimeString("ca-ES",{timeZone:"Europe/Madrid"})+".",url:"/tmb-agent/proves/servei/"}),{TTL:60,urgency:"high",timeout:15000}).catch(e=>{console.warn("Station push failed",e.statusCode||e.message);if(e.statusCode===410||e.statusCode===404)stationWatches.delete(endpoint)});
  }
 }
}
function watchNeeded(now=Date.now()) { return now-lastUse<120000 || now<testWatchUntil || [...stationWatches.values()].some(w=>now<w.until); }
function testTrainState(now=Date.now()) {
 const ageSeconds=receivedAt?Math.max(0,Math.floor((now-receivedAt)/1000)):null;
 return {active:!!(now<testWatchUntil&&connected&&ageSeconds!==null&&ageSeconds<45&&snapshot['403']),ageSeconds};
}
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
    while (!stopping && watchNeeded()) {
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
        while (!stopping && watchNeeded()) {
          const packets = (await request(params, undefined, signal)).split('\x1e');
          for (const packet of packets) {
            if (packet === '2') await request(params, '3', signal);
            else if (packet.startsWith('40')) connected = true;
            else if (packet === '1' || packet.startsWith('41')) throw new Error('Upstream closed');
            else if (packet.startsWith('42')) {
              const [event, data] = JSON.parse(packet.slice(2));
              if (event === 'mapUpdate' && data?.L4?.trains) {
                stationPasses(data.L4.trains);
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
      if (!stopping && watchNeeded()) {
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
 if(!['/api/push-test','/api/push-station'].includes(url.pathname)||req.method!=='POST'){res.writeHead(404);res.end('{}');return}
 if(!webpush){res.writeHead(503);res.end(JSON.stringify({error:'Push no disponible. Revisa el Build Command.'}));return}
 try{
  let body='';for await(const chunk of req){body+=chunk;if(body.length>8192)throw Error('Peticio massa gran')}
  const {subscription,until}=JSON.parse(body);
  const endpoint=new URL(subscription?.endpoint);
  const allowed=endpoint.hostname==='fcm.googleapis.com'||endpoint.hostname==='updates.push.services.mozilla.com'||endpoint.hostname.endsWith('.push.apple.com')||endpoint.hostname==='web.push.apple.com';
  if(endpoint.protocol!=='https:'||endpoint.port||endpoint.username||endpoint.password||!allowed||!subscription?.keys?.p256dh||!subscription?.keys?.auth)throw Error('Subscripció push no vàlida');
  if(url.pathname==='/api/push-station'){
   const end=Date.parse(until);
   // This pilot has one explicitly agreed deadline, in Barcelona time.
   const deadline=Date.parse('2026-10-31T00:57:00+01:00');
   if(!Number.isFinite(end)||end>Date.now()+9*3600000||end>deadline||end<=Date.now())throw Error('La prova ha de finalitzar com a màxim a les 00:57 del 31/10.');
   if(!stationWatches.has(endpoint.href)&&stationWatches.size>=10)throw Error('Massa proves actives');
   stationWatches.set(endpoint.href,{subscription,until:end,sent:new Map()});
   lastUse=Date.now();start();
   res.writeHead(202);res.end(JSON.stringify({until:new Date(end).toISOString(),station:417,tracks:[1,2],temporary:true}));return;
  }
  if(pendingPush.size>=10||pendingPush.has(endpoint.href))throw Error('Ja hi ha una prova pendent. Espera que acabi la prova de cinc minuts.');
  const scheduledAt=Date.now()+PUSH_TEST_MS;
  testWatchUntil=Math.max(testWatchUntil,scheduledAt+20000);
  const timer=setTimeout(async()=>{
   try{
    const t=snapshot['403'],state=testTrainState();
    const body=state.active?'Actiu · 403: '+t.estacion+' · via '+(t.platformTrack??t.track)+' · última recepció fa '+state.ageSeconds+' s. Prova de 5 minuts.':'Inactiu · 403: '+(!t?'tren no localitzat':!connected?'sense connexió':'dades antigues')+' · última recepció '+(state.ageSeconds===null?'no disponible':'fa '+state.ageSeconds+' s')+'. Prova de 5 minuts.';
    await webpush.sendNotification(subscription,JSON.stringify({title:'El meu servei · Prova push',body,url:'/tmb-agent/proves/servei/'}),{TTL:120,urgency:'high',timeout:15000});
    console.log('Push test accepted by delivery service');
   }catch(e){console.warn('Push test failed:',e.statusCode||e.message)}
   finally{pendingPush.delete(endpoint.href)}
  },PUSH_TEST_MS);
  pendingPush.set(endpoint.href,timer);lastUse=Date.now();start();
  res.writeHead(202);res.end(JSON.stringify({scheduledAt:new Date(scheduledAt).toISOString(),testMinutes:5,temporary:true}));
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
    ageSeconds, pushTest:{until:testWatchUntil?new Date(testWatchUntil).toISOString():null,...testTrainState()}, note:'Reception time is not the source measurement time. secondsLeft is not necessarily time to your relief.',trains}));
});
server.listen(Number(process.env.PORT || 10000), '0.0.0.0', () => console.log('Service listening'));
function shutdown() { stopping=true; controller?.abort(); server.close(); setTimeout(()=>process.exit(0),1500).unref(); }
process.on('SIGTERM',shutdown); process.on('SIGINT',shutdown);
