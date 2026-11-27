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
test('one server capability request validates identity and approval before opening launcher', async () => {
  const calls=[];
  const request=async(url,options)=>{calls.push({url,options});return reply({ok:true,can_manage:false});};
  assert.equal(await checkSession(storage(session),request),'approved');
  assert.equal(calls.length,1);
  assert.equal(calls[0].options.method,'POST');
  assert.deepEqual(JSON.parse(calls[0].options.body),{action:'capabilities'});
});
test('server denial blocks launcher without trusting local data', async()=>{
  assert.equal(await checkSession(storage(session),async()=>reply({error:'Accés denegat'},403)),'login');
});
test('network failure never grants access', async()=>{
  await assert.rejects(checkSession(storage(session),async()=>{throw new Error('offline');}),/offline/);
});
test('login URL includes no tokens and preserves route only',()=>{
  assert.equal(loginUrl({pathname:'/tmb-agent/',hash:'#/dea'}),'/tmb-agent/auth/?returnTo=%2Ftmb-agent%2F%23%2Fdea');
});
