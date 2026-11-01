self.addEventListener('install',()=>self.skipWaiting());
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('push',event=>{
 let data={};try{data=event.data.json()}catch{}
 const url=data.url||'/tmb-agent/proves/servei/';
 event.waitUntil((async()=>{
  const clients=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  const visible=clients.filter(client=>client.visibilityState==='visible');
  if(visible.length){for(const client of visible)client.postMessage({type:'service-alert',title:data.title||'El meu servei',body:data.body||'Avís del servei'});return}
  await self.registration.showNotification(data.title||'El meu servei',{body:data.body||'Prova push',icon:'/tmb-agent/assets/icons/miralin-192.png',tag:data.tag||'servei-push-test',renotify:false,data:{url}});
 })());
});
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 event.waitUntil(clients.openWindow(event.notification.data?.url||'/tmb-agent/proves/servei/'));
});
