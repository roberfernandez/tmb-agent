import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import { runInNewContext } from 'node:vm';
import { proposalCard } from '../src/bustia.js';

const source = stripTypeScriptTypes(readFileSync(new URL('../supabase/functions/tmb-agent-bustia/index.ts', import.meta.url), 'utf8').replace(/^import .*;\n/gm, ''));
async function invoke({ admin = false, approved = true, authenticated = true, missing = false, dbError = false, proposalState, body = {} } = {}) {
  let handler;
  const writes = [];
  const user = { id: 'test-user', email: 'test@example.invalid', app_metadata: { bustia_admin: admin }, user_metadata: { bustia_admin: true } };
  const client = {
    auth: { getUser: async () => ({ data: { user: authenticated ? user : null } }) },
    from(table) {
      const query = {
        select() { return this; }, eq() { return this; }, limit() { return this; }, order() { return this; },
        update(value) { writes.push({ table, value }); return this; },
        maybeSingle: async () => table === 'solicitudes_acceso'
          ? { data: approved ? { id: 1 } : null }
          : { data: missing ? null : { id: 1, estat: proposalState ?? body.estat }, error: dbError ? {} : null },
        then(resolve) { resolve({ data: [] }); },
      };
      return query;
    },
  };
  runInNewContext(source, { Response, console, createClient: () => client,
    Deno: { env: { get: () => 'test-only' }, serve: fn => { handler = fn; } },
    fetch: () => { throw Error('Status changes must not send Telegram'); },
  });
  const response = await handler(new Request('https://example.invalid', { method: 'POST', headers: { Authorization: 'Bearer test' }, body: JSON.stringify(body) }));
  return { status: response.status, data: await response.json(), writes };
}

test('ordinary approved user cannot change status, even with forged admin flags', async () => {
  const r = await invoke({ body: { action: 'set_status', proposta_id: 1, estat: 'feta', can_manage: true } });
  assert.equal(r.status, 403); assert.equal(r.writes.length, 0);
});
test('missing session and unapproved administrator are blocked', async () => {
  for (const options of [{ authenticated: false }, { admin: true, approved: false }]) {
    const r = await invoke({ ...options, body: { action: 'set_status', proposta_id: 1, estat: 'feta' } });
    assert.ok([401, 403].includes(r.status)); assert.equal(r.writes.length, 0);
  }
});
test('approved administrator can set all six states without editing proposal content', async () => {
  for (const estat of ['oberta', 'en_estudi', 'acceptada', 'en_desenvolupament', 'feta', 'descartada']) {
    const r = await invoke({ admin: true, body: { action: 'set_status', proposta_id: 1, estat, titol: 'Do not change' } });
    assert.equal(r.status, 200); assert.equal(r.data.proposta.estat, estat);
    assert.equal(JSON.stringify(r.writes), JSON.stringify([{ table: 'tmb_agent_propostes', value: { estat } }]));
  }
});
test('invalid states and IDs do not write; nonexistent and failed updates report errors', async () => {
  for (const [proposta_id, estat] of [[0, 'feta'], ['1', 'feta'], [1, 'inventat']]) {
    const r = await invoke({ admin: true, body: { action: 'set_status', proposta_id, estat } });
    assert.equal(r.status, 400); assert.equal(r.writes.length, 0);
  }
  for (const [options, status] of [[{ missing: true }, 404], [{ dbError: true }, 500]]) {
    assert.equal((await invoke({ admin: true, ...options, body: { action: 'set_status', proposta_id: 1, estat: 'feta' } })).status, status);
  }
});
test('list exposes management capability only from server-owned metadata', async () => {
  assert.equal((await invoke({ body: { action: 'list' } })).data.can_manage, false);
  assert.equal((await invoke({ admin: true, body: { action: 'list' } })).data.can_manage, true);
});
test('completed proposals reject both support actions on the server', async () => {
  for (const action of ['vote', 'unvote']) {
    const r = await invoke({ proposalState: 'feta', body: { action, proposta_id: 1 } });
    assert.equal(r.status, 409); assert.equal(r.writes.length, 0);
  }
});

class Element {
  children = []; dataset = {}; listeners = {}; classList = { toggle() {} };
  constructor(tag) { this.tag = tag; }
  append(...children) { this.children.push(...children); }
  setAttribute() {}
  addEventListener(event, callback) { this.listeners[event] = callback; }
}
function descendants(node) { return [node, ...node.children.flatMap(descendants)]; }
test('administrator can select Feta and save; errors retain controls; ordinary card has no management', async () => {
  const previous = globalThis.document;
  globalThis.document = { createElement: tag => new Element(tag) };
  try {
    const item = { id: 1, titol: 'Example', estat: 'oberta', vots: 1 };
    const completed = descendants(proposalCard({ ...item, estat: 'feta' }, () => {}));
    assert.ok(completed.some(n => n.textContent === 'Feta'));
    assert.ok(!completed.some(n => n.tag === 'button'));
    assert.ok(completed.some(n => n.textContent === '1 suport'));
    assert.ok(descendants(proposalCard(item, () => {})).some(n => n.textContent === 'M’hi sumo'));
    assert.equal(descendants(proposalCard(item, () => {})).filter(n => n.tag === 'select').length, 0);
    let saved;
    const card = proposalCard(item, () => {}, async (_, value) => { saved = value; throw Error('Offline'); });
    const nodes = descendants(card), select = nodes.find(n => n.tag === 'select');
    const save = nodes.find(n => n.textContent === 'Desar estat');
    assert.equal(select.children.length, 6);
    select.value = 'feta'; await save.listeners.click();
    assert.equal(saved, 'feta'); assert.equal(save.disabled, false); assert.equal(select.disabled, false);
    assert.ok(nodes.some(n => n.textContent === 'Offline'));
  } finally { globalThis.document = previous; }
});
