import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const source = await readFile(new URL('../public/sw.js', import.meta.url), 'utf8');
const scope = 'https://example.com/tmb-agent/';
function worker() {
  const handlers = {};
  const matches = [];
  const cached = { text: 'cached public module' };
  const context = {
    self: { registration: { scope }, addEventListener: (name, cb) => handlers[name] = cb },
    caches: { open: async () => ({ match: async (req, opts) => {
      matches.push({ req, opts });
      return typeof req === 'object' && req.url.includes('/src/') ? cached : undefined;
    } }) },
    fetch: async () => { throw new Error('offline'); }, URL, Response,
  };
  vm.runInNewContext(source, context);
  const send = async (request) => {
    let response;
    handlers.fetch({ request, respondWith: value => response = value });
    return response === undefined ? undefined : await response;
  };
  return { send, matches, cached };
}
test('offline modules use only this build cache, including versioned URLs', async () => {
  const w = worker();
  const response = await w.send({ method: 'GET', url: `${scope}src/main.js?v=test`, mode: 'cors' });
  assert.equal(response, w.cached);
  assert.equal(w.matches[0].opts.ignoreSearch, true);
});
test('Auth APIs and mutations never enter the cache handler', async () => {
  const w = worker();
  assert.equal(await w.send({ method: 'GET', url: 'https://project.supabase.co/auth/v1/user' }), undefined);
  assert.equal(await w.send({ method: 'POST', url: `${scope}anything` }), undefined);
  assert.equal(w.matches.length, 0);
});
test('offline Auth navigation never receives the launcher HTML', async () => {
  const w = worker();
  const response = await w.send({ method: 'GET', url: `${scope}auth/?recovery=1`, mode: 'navigate' });
  assert.equal(response.type, 'error');
  assert.equal(w.matches.length, 1);
});

test('install deduplicates shared files added by the build', async () => {
  let install;
  let urls;
  vm.runInNewContext(source.replace('const FILES = [', "const FILES = ['src/session.js','src/session.js',"), {
    self: { registration: { scope }, addEventListener: (name, handler) => { if (name === 'install') install = handler; } },
    caches: { open: async () => ({ addAll: async items => { urls = items; } }) }, URL,
  });
  let done;
  install({ waitUntil: value => done = value });
  await done;
  assert.equal(urls.length, new Set(urls).size);
});
