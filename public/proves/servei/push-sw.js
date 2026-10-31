self.addEventListener('install',()=>self.skipWaiting());
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('push',event=>{
 let data={};try{data=event.data.json()}catch{}
 const url=data.url||'/tmb-agent/proves/servei/';
 event.waitUntil(self.registration.showNotification(data.title||'El meu servei',{body:data.body||'Prova push',icon:'/tmb-agent/assets/icons/miralin-192.png',tag:data.tag||'servei-push-test',renotify:false,data:{url}}));
});
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 event.waitUntil(clients.openWindow('/tmb-agent/proves/servei/'));
});
