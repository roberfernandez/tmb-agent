import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
const base='https://roberfernandez.github.io/tmb-agent/';
const root=JSON.parse(readFileSync('public/manifest.webmanifest','utf8'));
const legacy=JSON.parse(readFileSync('public/proves/servei/manifest.webmanifest','utf8'));
test('normal launch opens the main menu and keeps the installed root identity',()=>{
 assert.equal(new URL(root.id,base+'manifest.webmanifest').href,base);
 assert.equal(new URL(root.start_url,base+'manifest.webmanifest').href,base+'#/');
 assert.equal(root.launch_handler.client_mode,'navigate-existing');
});
test('service page installs the same app as the menu, preserving direct service and viewer URLs',()=>{
 const html=readFileSync('public/proves/servei/index.html','utf8');
 const href=html.match(/rel="manifest" href="([^"]+)"/)[1];
 assert.equal(new URL(href,base+'proves/servei/').href,base+'manifest.webmanifest');
 assert.ok(html.includes("has('serviceViewer')"));
 assert.ok(html.includes("register('./push-sw.js'"));
});
test('both current and legacy installations expose the same three valid quick actions',()=>{
 const urls=[];
 for(const [manifest,url] of [[root,base+'manifest.webmanifest'],[legacy,base+'proves/servei/manifest.webmanifest']]){
  assert.equal(new URL(manifest.start_url,url).href,base+'#/');
  assert.equal(new URL(manifest.scope,url).href,base);
  assert.equal(manifest.shortcuts.length,3);
  urls.push(manifest.shortcuts.map(s=>new URL(s.url,url).href));
  for(const icon of [...manifest.icons,...manifest.shortcuts.flatMap(s=>s.icons)]){
   const path=new URL(icon.src,url).pathname.replace('/tmb-agent/','public/');
   assert.ok(existsSync(path),path);
  }
 }
 assert.deepEqual(urls[0],urls[1]);
 assert.deepEqual(urls[0].map(u=>new URL(u).hash),['#/miralin','#/incidencias','#/t-mobilitat']);
 assert.equal(new URL(legacy.id,base+'proves/servei/manifest.webmanifest').href,base+'proves/servei/');
});
