import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const parent=readFileSync(new URL('../public/proves/servei/index.html',import.meta.url),'utf8');
const pilot=readFileSync(new URL('../public/proves/servei/iphone/index.html',import.meta.url),'utf8');
const manifest=JSON.parse(readFileSync(new URL('../public/proves/servei/iphone/manifest.webmanifest',import.meta.url),'utf8'));

test('iPhone entry opens the Safari pilot and never the retired native scheme',()=>{
 assert.match(parent,/id="iosOption"[^>]+href="\.\/iphone\/"/);
 assert.match(parent,/function openIPhonePilot/);
 assert.doesNotMatch(parent,/tmbservei-ios:\/\//);
});

test('iPhone paths do not start the old Render push implementation',()=>{
 assert.match(parent,/if\(isIOS\)\{\s*if\(stop\)/);
 assert.match(parent,/if\(isIOS\)\{status\.textContent='Seguiment iPhone gestionat/);
});

test('pilot has only the official corporate data origin',()=>{
 assert.match(pilot,/https:\/\/aplicacions\.tmb\.cat\/miralin\/routes\/4\/stops\//);
 assert.doesNotMatch(pilot,/wss?:\/\/miralin\.cat|tmb-servei-proves\.onrender\.com/);
 assert.match(pilot,/credentials:'include'/);
});

test('pilot is an installable standalone PWA with an explicit foreground limitation',()=>{
 assert.equal(manifest.display,'standalone');
 assert.equal(manifest.start_url,'./');
 assert.match(pilot,/seguiment amb l’iPhone bloquejat/);
 assert.match(pilot,/Notification\.requestPermission\(\)/);
});
