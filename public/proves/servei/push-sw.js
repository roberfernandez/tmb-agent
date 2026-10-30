self.addEventListener('install',()=>self.skipWaiting());
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('push',event=>{
 let data={};try{data=event.data.json()}catch{}
 event.waitUntil(self.registration.showNotification(data.title||'El meu servei',{body:data.body||'Prova push',icon:'/tmb-agent/assets/icons/miralin-192.png',tag:'servei-push-test',data:{url:'/tmb-agent/proves/servei/'}}));
});
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 event.waitUntil(clients.openWindow('/tmb-agent/proves/servei/'));
});
