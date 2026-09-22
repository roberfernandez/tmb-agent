import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkSession, accessState, SESSION_KEY, loginUrl } from '../src/session.js';

const storage = (session) => ({getItem:key => key === SESSION_KEY ? JSON.stringify(session) : null});
const session = {access_token:'test-token-not-a-real-session', expires_at:Math.floor(Date.now()/1000)+3600, user:{id:'untrusted-local-id'}};
const reply = (body, status=200) => ({ok:status>=200&&status<300,status,json:async()=>body});

test('same precedence as Incidencias: approval prevails', () => {
  assert.equal(accessState([{estado:'rechazado'},{estado:'aprobado'}]),'aprobado');
  assert.equal(accessState([{estado:'pendiente'},{estado:'rechazado'}]),'pendiente');
  assert.equal(accessState([{estado:'blocked'}]),null);
});
test('missing or expired session cannot open launcher', async () => {
  const request=()=>{throw new Error('must not request');};
  assert.equal(await checkSession(storage(null),request),'login');
  assert.equal(await checkSession(storage({...session,expires_at:1}),request),'login');
});
test('approved user validated against Auth; ignores stored identity', async () => {
  const calls=[];
  const request=async(url,options)=>{calls.push(url);assert.equal(options.cache,'no-store');return reply(calls.length===1?{id:'server-user'}:[{estado:'aprobado'}]);};
  assert.equal(await checkSession(storage(session),request),'approved');
  assert.ok(calls[1].includes('user_id=eq.server-user'));
  assert.ok(!calls[1].includes('untrusted-local-id'));
});
for (const estado of ['pendiente','rechazado','blocked',null]) {
  test(`unauthorized state ${estado} denies launcher`, async () => {
    let n=0;
    assert.equal(await checkSession(storage(session),async()=>reply(++n===1?{id:'server-user'}:[{estado}])),'login');
  });
}
test('invalid server session denies access', async()=>{
  assert.equal(await checkSession(storage(session),async()=>reply({},401)),'login');
});
test('network failure never grants cached approval', async()=>{
  await assert.rejects(checkSession(storage(session),async()=>{throw new Error('offline');}));
});
test('restoration reads same session again and revalidates remotely', async()=>{
  const request=async(url)=>reply(url.includes('/auth/')?{id:'server-user'}:[{estado:'aprobado'}]);
  for (let n=0;n<2;n++) assert.equal(await checkSession(storage(session),request),'approved');
});
test('login URL includes no tokens, preserves route only',()=>{
  assert.equal(loginUrl({pathname:'/tmb-agent/',hash:'#/dea'}),'/tmb-agent/auth/?returnTo=%2Ftmb-agent%2F%23%2Fdea');
});
