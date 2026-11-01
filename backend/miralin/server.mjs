import http from 'node:http';
import {createHash,randomUUID,createSign} from 'node:crypto';
import http2 from 'node:http2';
const bootId=randomUUID();
function diagnostic(event,details={}) { console.log(JSON.stringify({at:new Date().toISOString(),bootId,event,...details})); }
const watchId=endpoint=>createHash('sha256').update(endpoint).digest('hex').slice(0,12);
let clientRequests=0,lastClientRequestAt=0,mapUpdates=0;
diagnostic('server_boot');
const UPSTREAM = 'https://miralin.cat/socket.io/';
const ORIGIN = 'https://roberfernandez.github.io';
let snapshot = {}, receivedAt = 0, connected = false, running = false, lastUse = Date.now(), stopping = false;
let controller;
const PUSH_TEST_MS = 5 * 60 * 1000;
let testWatchUntil = 0;
const stationWatches=new Map();
const enduranceWatches=new Map();
const trainWatches=new Map();
const liveActivityWatches=new Map();
const stationBoardWaiters=new Map();
let activePollingParams=null,activePollingSignal=null;
const ENDURANCE_INTERVAL=10*60*1000,ENDURANCE_COUNT=48;
const TRAIN_WATCH_INTERVAL=5*60*1000,TRAIN_WATCH_COUNT=4;
function enduranceBody(w,now=Date.now()){
 const age=receivedAt?Math.max(0,Math.floor((now-receivedAt)/1000)):null;
 const t=snapshot['417'],active=!!(connected&&age!==null&&age<45&&t);
 return (active?'Actiu · 417: '+t.estacion+' · via '+(t.platformTrack??t.track):'Inactiu · 417: '+(!connected?'sense connexió':age===null||age>=45?'sense dades recents':'tren no localitzat'))+
  ' · recepció '+(age===null?'no disponible':'fa '+age+' s')+' · prova '+w.count+'/48 · '+new Date(now).toLocaleTimeString('ca-ES',{timeZone:'Europe/Madrid'})+'. La recepció no és la data de mesura de la font.';
}
async function enduranceTick(endpoint,w,now=Date.now()){
 if(enduranceWatches.get(endpoint)!==w||w.busy||now<w.nextAt)return;
 w.busy=true;w.count++;w.nextAt=now+ENDURANCE_INTERVAL;
 diagnostic('endurance_push_attempt',{watchId:watchId(endpoint),sequence:w.count,connected,receptionAgeSeconds:receivedAt?Math.floor((now-receivedAt)/1000):null});
 try{
  const result=await webpush.sendNotification(w.subscription,JSON.stringify({title:'Prova 8 hores · 417 · '+w.count+'/48',body:enduranceBody(w,now),url:'/tmb-agent/proves/servei/'}),{TTL:300,urgency:'high',timeout:15000});
  diagnostic('endurance_push_accepted',{watchId:watchId(endpoint),sequence:w.count,statusCode:result.statusCode});
 }catch(e){diagnostic('endurance_push_failed',{watchId:watchId(endpoint),sequence:w.count,statusCode:e.statusCode||null,errorName:e.name});if(e.statusCode===404||e.statusCode===410){clearInterval(w.timer);enduranceWatches.delete(endpoint)}}
 finally{w.busy=false;if(w.count>=ENDURANCE_COUNT){clearInterval(w.timer);enduranceWatches.delete(endpoint);diagnostic('endurance_watch_finished',{watchId:watchId(endpoint),attempts:w.count})}}
}
function trainWatchBody(w,now=Date.now()){
 const age=receivedAt?Math.max(0,Math.floor((now-receivedAt)/1000)):null;
 const t=snapshot[w.train],fresh=connected&&age!==null&&age<45&&t;
 const place=fresh?(t.estacion+' · v.'+(t.platformTrack??t.track)):'Sense dades';
 return place+' · '+(age===null?'—':'fa '+age+' s');
}
async function trainWatchTick(endpoint,w,now=Date.now()){
 if(trainWatches.get(endpoint)!==w||w.busy||now<w.nextAt)return;
 if(now>=w.until||w.count>=w.maxCount){clearInterval(w.timer);trainWatches.delete(endpoint);diagnostic('train_watch_finished',{watchId:watchId(endpoint),train:w.train,updates:w.count});return}
 w.busy=true;w.count++;w.nextAt=now+TRAIN_WATCH_INTERVAL;
 diagnostic('train_push_attempt',{watchId:watchId(endpoint),train:w.train,mode:w.mode,sequence:w.count,receptionAgeSeconds:receivedAt?Math.floor((now-receivedAt)/1000):null});
 try{
  const result=await webpush.sendNotification(w.subscription,JSON.stringify({title:'Tren '+w.train+' · seguiment',body:trainWatchBody(w,now),tag:'train-watch-'+w.train,url:'/tmb-agent/proves/servei/?watch='+w.train}),{TTL:30,urgency:'high',timeout:15000});
  diagnostic('train_push_accepted',{watchId:watchId(endpoint),train:w.train,mode:w.mode,sequence:w.count,statusCode:result.statusCode});
 }catch(e){
  diagnostic('train_push_failed',{watchId:watchId(endpoint),train:w.train,sequence:w.count,statusCode:e.statusCode||null,errorName:e.name});
  if(e.statusCode===404||e.statusCode===410){clearInterval(w.timer);trainWatches.delete(endpoint)}
 }finally{
  w.busy=false;
  if(trainWatches.get(endpoint)===w&&(w.count>=w.maxCount||Date.now()>=w.until)){clearInterval(w.timer);trainWatches.delete(endpoint);diagnostic('train_watch_finished',{watchId:watchId(endpoint),train:w.train,updates:w.count})}
 }
}
let previousMap=null,previousMapAt=0;
function stationPasses(trains,now=Date.now()) {
 const previous=previousMap;const fresh=previous&&now-previousMapAt<45000;
 previousMap=trains;previousMapAt=now;
 for(const [endpoint,w] of stationWatches){
  if(now>=w.until){diagnostic('station_watch_expired',{watchId:watchId(endpoint)});stationWatches.delete(endpoint);continue}
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
   diagnostic('station_pass_detected',{watchId:watchId(endpoint),train:id,via,station:417});
   void webpush.sendNotification(w.subscription,JSON.stringify({title:"Selva de Mar · Tren "+id+" · via "+via,body:"Pas detectat segons el canvi d’estació de Miralin. Recepció "+new Date(now).toLocaleTimeString("ca-ES",{timeZone:"Europe/Madrid"})+".",url:"/tmb-agent/proves/servei/"}),{TTL:60,urgency:"high",timeout:15000}).then(result=>diagnostic('station_push_accepted',{watchId:watchId(endpoint),train:id,via,statusCode:result.statusCode})).catch(e=>{diagnostic('station_push_failed',{watchId:watchId(endpoint),train:id,via,statusCode:e.statusCode||null,errorName:e.name});if(e.statusCode===410||e.statusCode===404)stationWatches.delete(endpoint)});
  }
 }
}
function watchNeeded(now=Date.now()) { return [...enduranceWatches.values()].some(w=>w.count<ENDURANCE_COUNT||w.busy) || [...trainWatches.values()].some(w=>w.count<w.maxCount||w.busy) || now-lastUse<120000 || now<testWatchUntil || [...stationWatches.values()].some(w=>now<w.until) || stationBoardWaiters.size>0 || (apnsConfigured && [...liveActivityWatches.values()].some(w=>now<w.until)); }
function testTrainState(now=Date.now()) {
 const ageSeconds=receivedAt?Math.max(0,Math.floor((now-receivedAt)/1000)):null;
 return {active:!!(now<testWatchUntil&&connected&&ageSeconds!==null&&ageSeconds<45&&snapshot['403']),ageSeconds};
}
const pause = ms => new Promise(r => setTimeout(r, ms));
async function subscribeStopBoard(params,signal,stop){
 await request(params,'42'+JSON.stringify(['subscribeStop',{line:'L4',stop:String(stop)}]),signal);
}
function acceptStopBoard(data){
 const values=[data?.stop,data?.stopId,data?.stationId,data?.stationCode,data?.id];
 let key=values.map(x=>String(x??'')).find(x=>stationBoardWaiters.has(x));
 if(!key&&stationBoardWaiters.size===1)key=stationBoardWaiters.keys().next().value;
 if(!key)return;
 const pending=stationBoardWaiters.get(key);stationBoardWaiters.delete(key);
 for(const waiter of pending.waiters){clearTimeout(waiter.timer);waiter.resolve(data)}
 diagnostic('station_board_received',{station:key,keys:Object.keys(data||{}).slice(0,20)});
}
function requestStopBoard(stop){
 return new Promise((resolve,reject)=>{
  const key=String(stop);let pending=stationBoardWaiters.get(key);
  if(!pending){pending={waiters:[],sent:false};stationBoardWaiters.set(key,pending)}
  const waiter={resolve,reject,timer:setTimeout(()=>{
   const current=stationBoardWaiters.get(key);if(current){current.waiters=current.waiters.filter(w=>w!==waiter);if(!current.waiters.length)stationBoardWaiters.delete(key)}
   reject(new Error('Miralin no ha retornat l’horari d’arribades de l’estació '+key+' a temps.'));
  },30000)};
  pending.waiters.push(waiter);
  if(activePollingParams&&activePollingSignal&&!activePollingSignal.aborted&&!pending.sent){
   pending.sent=true;void subscribeStopBoard(activePollingParams,activePollingSignal,key).catch(e=>{pending.sent=false;diagnostic('station_board_subscribe_failed',{station:key,errorName:e.name})});
  }
  lastUse=Date.now();start();
 });
}
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
  diagnostic('upstream_loop_started');
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
        activePollingParams=params;activePollingSignal=signal;
        for(const [stop,pending] of stationBoardWaiters){pending.sent=true;await subscribeStopBoard(params,signal,stop)}
        while (!stopping && watchNeeded()) {
          const packets = (await request(params, undefined, signal)).split('\x1e');
          for (const packet of packets) {
            if (packet === '2') await request(params, '3', signal);
            else if (packet.startsWith('40')) connected = true;
            else if (packet === '1' || packet.startsWith('41')) throw new Error('Upstream closed');
            else if (packet.startsWith('42')) {
              const [event, data] = JSON.parse(packet.slice(2));
              if(event==='stopDataUpdate'){acceptStopBoard(data)}
              else if (event === 'mapUpdate' && data?.L4?.trains) {
                stationPasses(data.L4.trains);
                snapshot = data.L4.trains;
                if(!connected||!receivedAt||Date.now()-receivedAt>45000)diagnostic('upstream_data_resumed',{previousAgeSeconds:receivedAt?Math.floor((Date.now()-receivedAt)/1000):null});
                mapUpdates++;
                receivedAt = Date.now();
                connected = true;
                retry = 2000;
              }
            }
          }
        }
      } catch (e) {
        connected = false;
        if (!stopping) { console.warn('Connection interrupted:', e.message); diagnostic('upstream_interrupted',{errorName:e.name}); }
      } finally {
        if(activePollingSignal===signal){activePollingParams=null;activePollingSignal=null}
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
    diagnostic('upstream_loop_stopped',{stopping,watchNeeded:watchNeeded()});
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
 if(!['/api/push-test','/api/push-station','/api/push-endurance','/api/push-train-watch'].includes(url.pathname)||req.method!=='POST'){res.writeHead(404);res.end('{}');return}
 if(!webpush){res.writeHead(503);res.end(JSON.stringify({error:'Push no disponible. Revisa el Build Command.'}));return}
 try{
  let body='';for await(const chunk of req){body+=chunk;if(body.length>8192)throw Error('Peticio massa gran')}
  const {subscription,until}=JSON.parse(body);
  const endpoint=new URL(subscription?.endpoint);
  const allowed=endpoint.hostname==='fcm.googleapis.com'||endpoint.hostname==='updates.push.services.mozilla.com'||endpoint.hostname.endsWith('.push.apple.com')||endpoint.hostname==='web.push.apple.com';
  if(endpoint.protocol!=='https:'||endpoint.port||endpoint.username||endpoint.password||!allowed||!subscription?.keys?.p256dh||!subscription?.keys?.auth)throw Error('Subscripció push no vàlida');
  if(url.pathname==='/api/push-train-watch'){
   const payload=JSON.parse(body);
   const train=String(payload.train||'');
   const mode='update';
   if(!/^4\d{2}$/.test(train))throw Error('Introdueix un tren L4 de tres xifres.');
   if(payload.action==='stop'){
    const old=trainWatches.get(endpoint.href);
    if(old){clearInterval(old.timer);trainWatches.delete(endpoint.href);diagnostic('train_watch_stopped',{watchId:watchId(endpoint.href),train:old.train,updates:old.count})}
    res.writeHead(200);res.end(JSON.stringify({stopped:true,train:old?.train||train}));return;
   }
   /* El seguiment de prova sempre actualitza el mateix avís cada cinc minuts. */
   if(!trainWatches.has(endpoint.href)&&trainWatches.size>=10)throw Error('Massa seguiments actius.');
   const old=trainWatches.get(endpoint.href);if(old)clearInterval(old.timer);
   const startedAt=Date.now(),maxCount=TRAIN_WATCH_COUNT;
   const until=startedAt+20*60*1000;
   const w={subscription,train,mode,count:0,maxCount,busy:false,nextAt:startedAt+15000,until,timer:null};
   trainWatches.set(endpoint.href,w);
   w.timer=setInterval(()=>{void trainWatchTick(endpoint.href,w)},1000);
   lastUse=Date.now();start();
   diagnostic('train_watch_registered',{watchId:watchId(endpoint.href),train,mode,firstAt:new Date(w.nextAt).toISOString(),until:new Date(until).toISOString(),intervalMinutes:5,maxUpdates:maxCount});
   res.writeHead(202);res.end(JSON.stringify({train,mode,startedAt:new Date(startedAt).toISOString(),firstAt:new Date(w.nextAt).toISOString(),until:new Date(until).toISOString(),intervalMinutes:5,maxUpdates:maxCount,temporary:true}));return;
  }
  if(url.pathname==='/api/push-endurance'){
   if(!enduranceWatches.has(endpoint.href)&&enduranceWatches.size>=10)throw Error('Massa proves actives');
   const old=enduranceWatches.get(endpoint.href);if(old)clearInterval(old.timer);
   const startedAt=Date.now(),until=startedAt+ENDURANCE_INTERVAL*ENDURANCE_COUNT;
   const w={subscription,count:0,nextAt:startedAt+ENDURANCE_INTERVAL,busy:false,timer:null};
   enduranceWatches.set(endpoint.href,w);
   w.timer=setInterval(()=>{void enduranceTick(endpoint.href,w)},1000);
   lastUse=Date.now();start();
   diagnostic('endurance_watch_registered',{watchId:watchId(endpoint.href),until:new Date(until).toISOString(),train:'417',intervalMinutes:10,count:48});
   res.writeHead(202);res.end(JSON.stringify({startedAt:new Date(startedAt).toISOString(),until:new Date(until).toISOString(),firstAt:new Date(w.nextAt).toISOString(),intervalMinutes:10,count:48,temporary:true}));return;
  }
  if(url.pathname==='/api/push-station'){
   const end=Date.parse(until);
   // This pilot has one explicitly agreed deadline, in Barcelona time.
   const deadline=Date.parse('2026-10-31T00:57:00+01:00');
   if(!Number.isFinite(end)||end>Date.now()+9*3600000||end>deadline||end<=Date.now())throw Error('La prova ha de finalitzar com a màxim a les 00:57 del 31/10.');
   if(!stationWatches.has(endpoint.href)&&stationWatches.size>=10)throw Error('Massa proves actives');
   stationWatches.set(endpoint.href,{subscription,until:end,sent:new Map()});
   diagnostic('station_watch_registered',{watchId:watchId(endpoint.href),until:new Date(end).toISOString(),station:417,tracks:[1,2]});
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


const APNS_KEY_ID=process.env.APNS_KEY_ID||'';
const APNS_TEAM_ID=process.env.APNS_TEAM_ID||'';
const APNS_BUNDLE_ID=process.env.APNS_BUNDLE_ID||'cat.tmbagent.trainlive';
const APNS_PRIVATE_KEY=(process.env.APNS_PRIVATE_KEY||'').replace(/\\n/g,'\n');
const APNS_ENVIRONMENT=process.env.APNS_ENVIRONMENT==='sandbox'?'sandbox':'production';
const apnsConfigured=!!(APNS_KEY_ID&&APNS_TEAM_ID&&APNS_PRIVATE_KEY);
let cachedApnsJwt='',cachedApnsJwtAt=0;
function b64url(value){return Buffer.from(value).toString('base64url')}
function apnsJwt(){
 const now=Math.floor(Date.now()/1000);
 if(cachedApnsJwt&&now-cachedApnsJwtAt<45*60)return cachedApnsJwt;
 const header=b64url(JSON.stringify({alg:'ES256',kid:APNS_KEY_ID}));
 const claims=b64url(JSON.stringify({iss:APNS_TEAM_ID,iat:now}));
 const message=header+'.'+claims;
 const signature=createSign('SHA256').update(message).end().sign({key:APNS_PRIVATE_KEY,dsaEncoding:'ieee-p1363'});
 cachedApnsJwt=message+'.'+signature.toString('base64url');cachedApnsJwtAt=now;
 return cachedApnsJwt;
}
function sendActivityPush(token,environment,state){
 const host=environment==='sandbox'?'https://api.sandbox.push.apple.com':'https://api.push.apple.com';
 const client=http2.connect(host);
 const topic=APNS_BUNDLE_ID+'.push-type.liveactivity';
 const now=Math.floor(Date.now()/1000);
 const payload={aps:{timestamp:now,event:'update','stale-date':now+90,'content-state':state}};
 return new Promise((resolve,reject)=>{
  let code=0,raw='';
  const req=client.request({':method':'POST',':path':'/3/device/'+token,authorization:'bearer '+apnsJwt(),'apns-topic':topic,'apns-push-type':'liveactivity','apns-priority':'5','apns-expiration':'0','content-type':'application/json'});
  req.setEncoding('utf8');
  req.on('response',headers=>{code=Number(headers[':status']||0)});
  req.on('data',chunk=>{raw+=chunk});
  req.on('end',()=>{client.close();if(code===200)resolve({code});else reject(Object.assign(new Error(raw||'APNs rejected update'),{statusCode:code,apnsBody:raw}))});
  req.on('error',error=>{client.close();reject(error)});
  req.end(JSON.stringify(payload));
 });
}
function serviceMinute(w,now=new Date()){
 const parts=new Intl.DateTimeFormat('en-GB',{timeZone:'Europe/Madrid',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(now);
 const get=k=>Number(parts.find(p=>p.type===k)?.value||0);
 const today=Date.UTC(get('year'),get('month')-1,get('day'));
 const base=w.date.split('-').map(Number);
 if(base.length!==3||base.some(n=>!n))return get('hour')*60+get('minute');
 return Math.round((today-Date.UTC(base[0],base[1]-1,base[2]))/86400000)*1440+get('hour')*60+get('minute');
}
function activityTrain(w,now=new Date()){
 if(w.mode==='manual')return w.train;
 const minute=serviceMinute(w,now);
 const entries=w.schedule.split(';').map(part=>part.split(',')).filter(a=>a.length===3)
  .map(a=>({start:Number(a[0]),end:Number(a[1]),train:a[2]}))
  .filter(a=>Number.isInteger(a.start)&&Number.isInteger(a.end)&&a.end>a.start&&/^4\d{2}$/.test(a.train));
 const active=entries.find(a=>minute>=a.start&&minute<a.end);
 if(active)return active.train;
 return entries.find(a=>a.start>minute)?.train||entries.at(-1)?.train||w.train;
}
function activityContent(w,now=new Date()){
 const train=activityTrain(w,now),age=receivedAt?Math.max(0,Math.floor((now.getTime()-receivedAt)/1000)):null;
 const item=snapshot[train],stale=!connected||age===null||age>45||!item,raw=item?.status||'';
 const status=stale?'Dades antigues':raw==='arriving'?'Aproximació':raw==='en_route'?'En circulació':raw==='waiting_departure'?'Esperant sortida':raw||'Estat desconegut';
 return {train,station:item?.estacion||'Sense dades',track:String(item?.platformTrack??item?.track??'—'),status,destination:item?.destiLabel||'—',updatedAt:receivedAt?Math.floor(receivedAt/1000):Math.floor(now.getTime()/1000),stale};
}
async function liveActivityTick(id,w,now=Date.now()){
 if(liveActivityWatches.get(id)!==w||now>=w.until||!apnsConfigured||w.busy)return;
 const state=activityContent(w,new Date(now));
 const key=JSON.stringify([state.train,state.station,state.track,state.status,state.destination,state.stale]);
 const changed=key!==w.lastKey;
 if(!changed&&now-w.lastPushAt<5*60*1000)return;
 if(changed&&w.lastPushAt&&now-w.lastPushAt<20000)return;
 w.busy=true;w.lastKey=key;w.lastPushAt=now;
 try{
  const result=await sendActivityPush(w.token,w.environment,state);
  diagnostic('live_activity_update_sent',{watchId:watchId(id),train:state.train,changed,stale:state.stale,statusCode:result.code});
 }catch(e){
  diagnostic('live_activity_update_failed',{watchId:watchId(id),train:state.train,statusCode:e.statusCode||null,errorName:e.name,apnsBody:e.apnsBody||null});
  if(e.statusCode===410||e.statusCode===400)liveActivityWatches.delete(id);
 }finally{w.busy=false}
}
async function liveActivityRoute(req,res,url){
 try{
  let raw='';for await(const chunk of req){raw+=chunk;if(raw.length>16000)throw Error('Petició massa gran')}
  const data=JSON.parse(raw||'{}'),id=String(data.activityId||'');
  if(url.pathname==='/api/live-activity/stop'){if(id)liveActivityWatches.delete(id);res.writeHead(200);res.end(JSON.stringify({stopped:true}));return}
  if(url.pathname!=='/api/live-activity/start')throw Error('Ruta no vàlida');
  if(!/^[A-Za-z0-9-]{1,80}$/.test(id))throw Error('Identificador d’activitat no vàlid.');
  if(!/^[a-f0-9]{64,256}$/i.test(String(data.token||'')))throw Error('Token APNs no vàlid.');
  if(!['service','manual'].includes(data.mode))throw Error('Mode no vàlid.');
  const train=String(data.train||'');
  if(!/^4\d{2}$/.test(train)||Number(train)<401||Number(train)>415)throw Error('Tren L4 no vàlid.');
  const date=String(data.date||''),schedule=String(data.schedule||'');
  if(data.mode==='service'){
   if(!/^\d{4}-\d{2}-\d{2}$/.test(date)||!schedule||schedule.length>4096)throw Error('Falta la data o el servei.');
   const entries=schedule.split(';');
   if(entries.length>60||entries.some(e=>!/^\d{1,4},\d{1,4},4\d{2}$/.test(e)))throw Error('Horari del servei no vàlid.');
  }
  if(!liveActivityWatches.has(id)&&liveActivityWatches.size>=10)throw Error('Ja hi ha deu activitats de prova actives.');
  const until=Date.now()+8*60*60*1000;
  liveActivityWatches.set(id,{token:data.token,environment:data.environment==='sandbox'?'sandbox':'production',mode:data.mode,train,date,schedule,until,lastKey:'',lastPushAt:0,busy:false});
  lastUse=Date.now();if(apnsConfigured)start();
  diagnostic('live_activity_registered',{watchId:watchId(id),mode:data.mode,train,remoteUpdatesConfigured:apnsConfigured,until:new Date(until).toISOString()});
  res.writeHead(202);res.end(JSON.stringify({accepted:true,remoteUpdatesConfigured:apnsConfigured,until:new Date(until).toISOString()}));
 }catch(e){res.writeHead(400);res.end(JSON.stringify({error:e.message}))}
}
setInterval(()=>{
 const now=Date.now();
 for(const [id,w] of liveActivityWatches){
  if(now>=w.until){liveActivityWatches.delete(id);diagnostic('live_activity_expired',{watchId:watchId(id)});continue}
  void liveActivityTick(id,w,now);
 }
},15000).unref();

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
  if(pushUrl.pathname.startsWith('/api/live-activity/')){res.setHeader('Content-Type','application/json; charset=utf-8');if(req.method!=='POST'){res.writeHead(405);return res.end('{}')}void liveActivityRoute(req,res,pushUrl);return;}
  if(pushUrl.pathname.startsWith('/api/push-')){
    res.setHeader('Content-Type','application/json; charset=utf-8');
    if(req.method==='POST'&&origin!==ORIGIN){res.writeHead(403);return res.end('{}')}
    void pushRoute(req,res,pushUrl);return;
  }
  if (req.method !== 'GET') { res.writeHead(405); return res.end(); }
  const url = new URL(req.url, 'http://localhost');
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  if (url.pathname === '/health') return res.end(JSON.stringify({ok:true, connected}));
  if(url.pathname==='/api/station-board'){
    const ids=(url.searchParams.get('stops')||url.searchParams.get('stop')||'').split(',').filter(Boolean);
    if(!ids.length||ids.length>4||ids.some(id=>!/^4(?:1[3-9]|2[0-9]|3[0-4])$/.test(id))){res.writeHead(400);return res.end(JSON.stringify({error:'Invalid L4 station IDs'}))}
    lastClientRequestAt=Date.now();clientRequests++;
    void (async()=>{try{const boards=await Promise.all(ids.map(async id=>[id,await requestStopBoard(id)]));res.writeHead(200);res.end(JSON.stringify({source:'Miralin.cat',connected,receivedAt:new Date().toISOString(),stops:Object.fromEntries(boards)}))}
    catch(e){res.writeHead(503);res.end(JSON.stringify({error:e.message,connected,availableStations:ids}))}})();return;
  }
  if (!['/', '/api/trains'].includes(url.pathname)) { res.writeHead(404); return res.end(JSON.stringify({error:'Not found'})); }
  lastClientRequestAt=Date.now();clientRequests++;
  diagnostic('client_train_request',{count:clientRequests});
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
function shutdown() { diagnostic('server_shutdown');stopping=true; controller?.abort(); server.close(); setTimeout(()=>process.exit(0),1500).unref(); }
process.on('SIGTERM',shutdown); process.on('SIGINT',shutdown);

setInterval(()=>{
 diagnostic('watch_heartbeat',{
  connected,running,mapUpdates,enduranceWatches:[...enduranceWatches].map(([endpoint,w])=>({watchId:watchId(endpoint),attempts:w.count,nextAt:new Date(w.nextAt).toISOString()})),
  receptionAgeSeconds:receivedAt?Math.floor((Date.now()-receivedAt)/1000):null,
  clientRequests,lastClientRequestAt:lastClientRequestAt?new Date(lastClientRequestAt).toISOString():null,
  stationWatches:[...stationWatches].filter(([,w])=>w.until>Date.now()).map(([endpoint,w])=>({watchId:watchId(endpoint),until:new Date(w.until).toISOString()})),
  trainWatches:[...trainWatches].map(([endpoint,w])=>({watchId:watchId(endpoint),train:w.train,mode:w.mode,updates:w.count,nextAt:new Date(w.nextAt).toISOString()})),
  liveActivities:[...liveActivityWatches].map(([id,w])=>({watchId:watchId(id),mode:w.mode,train:w.train,until:new Date(w.until).toISOString()}))
 });
},60000).unref();
