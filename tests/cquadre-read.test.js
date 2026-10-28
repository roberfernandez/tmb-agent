import { test } from 'node:test';
import assert from 'node:assert/strict';
import { renderCQuadre } from '../src/cquadre.js';
import { renderCQuadre as renderCurrentView } from '../src/cquadre-view.js';
import { SESSION_KEY } from '../src/session.js';

class Element {
  constructor(tag) {
    this.tag = tag;
    this.className = '';
    this.textContent = '';
    this.children = [];
    this.listeners = {};
    this.classList = { toggle() {} };
  }
  append(...children) {
    for (const child of children) {
      this.children.push(child);
      child.parent = this;
    }
  }
  replaceChildren(...children) {
    this.children = [];
    this.append(...children);
  }
  setAttribute() {}
  addEventListener(event, callback) { this.listeners[event] = callback; }
  remove() {
    if (this.parent) this.parent.children = this.parent.children.filter(item => item !== this);
  }
}
function descendants(element) { return [element, ...element.children.flatMap(descendants)]; }
function elements(root, className) {
  return descendants(root).filter(item => item.className === className);
}
function makeFixture() {
  const originals = {
    document: globalThis.document,
    localStorage: globalThis.localStorage,
    fetch: globalThis.fetch,
  };
  const read = { complements_quadre_llegits: [], avisos_tmb_llegits: [] };
  const writes = [];
  let failWrites = false;
  globalThis.document = { createElement: tag => new Element(tag) };
  globalThis.localStorage = {
    getItem: key => key === SESSION_KEY
      ? JSON.stringify({ access_token: 'test-token', user: { id: 'test-user' } })
      : null,
  };
  globalThis.fetch = async (url, options = {}) => {
    const table = url.split('/rest/v1/')[1].split('?')[0];
    if (options.method === 'POST') {
      if (failWrites) return { ok: false, status: 403 };
      const row = JSON.parse(options.body);
      writes.push({ table, row });
      if (!read[table].some(value => JSON.stringify(value) === JSON.stringify(row))) read[table].push(row);
      return { ok: true, status: 201, json: async () => null };
    }
    const catalogs = {
      complements_quadre: [
        { id: 1, titulo: 'Complement A', enlace: 'https://example.org/a', fecha: '2026-10-14' },
        { id: 2, titulo: 'Complement B', enlace: 'https://example.org/b', fecha: '2026-10-15' },
      ],
      avisos_tmb: [{ id: 5, titulo: 'Avís A', enlace: 'https://example.org/avis', fecha: '2026-10-15' }],
    };
    return {
      ok: true, status: 200,
      json: async () => catalogs[table] ?? read[table].map(value =>
        table === 'complements_quadre_llegits'
          ? { complement_id: value.complement_id }
          : { aviso_id: value.aviso_id }),
    };
  };
  return {
    writes,
    fail() { failWrites = true; },
    restore() {
      globalThis.document = originals.document;
      globalThis.localStorage = originals.localStorage;
      globalThis.fetch = originals.fetch;
    },
  };
}
test('CQuadre supports selecting several items and marking them without opening SharePoint', async () => {
  const fixture = makeFixture();
  try {
    const counts = {};
    const root = await renderCQuadre((kind, count) => { counts[kind] = count; });
    const section = elements(root, 'pending-section')[0];
    assert.equal(elements(section, 'cquadre-item').length, 2);
    const all = elements(section, 'cquadre-select-all')[0];
    all.listeners.click();
    assert.equal(elements(section, 'cquadre-checkbox').filter(input => input.checked).length, 2);
    const mark = elements(section, 'button cquadre-mark-selected')[0];
    assert.equal(mark.disabled, false);
    await mark.listeners.click();
    assert.equal(elements(section, 'cquadre-item').length, 0);
    assert.equal(counts.cquadre, 0);
    assert.equal(fixture.writes.length, 2);
    assert.ok(fixture.writes.every(write => write.table === 'complements_quadre_llegits' &&
      write.row.user_id === 'test-user'));
    const reloaded = await renderCQuadre();
    assert.equal(elements(elements(reloaded, 'pending-section')[0], 'cquadre-item').length, 0);
  } finally { fixture.restore(); }
});
test('Avisos offers an individual read button and persists independently', async () => {
  const fixture = makeFixture();
  try {
    const counts = {};
    const root = await renderCQuadre((kind, count) => { counts[kind] = count; });
    const avisoSection = elements(root, 'pending-section')[1];
    const button = elements(avisoSection, 'cquadre-read-button')[0];
    button.listeners.click();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(counts.avisos, 0);
    assert.equal(elements(avisoSection, 'cquadre-item').length, 0);
    assert.deepEqual(fixture.writes[0], {
      table: 'avisos_tmb_llegits', row: { user_id: 'test-user', aviso_id: 5 },
    });
    const reloaded = await renderCQuadre();
    assert.equal(elements(elements(reloaded, 'pending-section')[1], 'cquadre-item').length, 0);
    assert.equal(elements(elements(reloaded, 'pending-section')[0], 'cquadre-item').length, 2);
  } finally { fixture.restore(); }
});
test('failed write leaves pending item visible and reports the error', async () => {
  const fixture = makeFixture();
  fixture.fail();
  try {
    const root = await renderCQuadre();
    const section = elements(root, 'pending-section')[0];
    elements(section, 'cquadre-read-button')[0].listeners.click();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(elements(section, 'cquadre-item').length, 2);
    assert.match(elements(section, 'cquadre-status')[0].textContent, /No s’ha pogut/);
  } finally { fixture.restore(); }
});

test('current view saves opened documents per user and keeps Avisos and C.Quadre independent', async () => {
  const fixture = makeFixture();
  try {
    const counts = {};
    const root = await renderCurrentView((kind, count) => { counts[kind] = count; });
    const sections = elements(root, 'pending-section');
    await elements(sections[0], 'cquadre-link')[0].listeners.click();
    assert.deepEqual(counts, { cquadre: 1 });
    assert.deepEqual(fixture.writes[0], {
      table: 'complements_quadre_llegits', row: { user_id: 'test-user', complement_id: 1 },
    });
    assert.equal(elements(sections[1], 'cquadre-item').length, 1);
    await elements(sections[1], 'cquadre-link')[0].listeners.click();
    assert.deepEqual(counts, { cquadre: 1, avisos: 0 });
    assert.deepEqual(fixture.writes[1], {
      table: 'avisos_tmb_llegits', row: { user_id: 'test-user', aviso_id: 5 },
    });
    const reloaded = elements(await renderCurrentView(), 'pending-section');
    assert.equal(elements(reloaded[0], 'cquadre-item').length, 1);
    assert.equal(elements(reloaded[1], 'cquadre-item').length, 0);
  } finally { fixture.restore(); }
});
