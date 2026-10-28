import { test } from 'node:test';
import assert from 'node:assert/strict';
import { renderPendingSection } from '../src/cquadre-view.js';

class Element {
  constructor(tag) {
    this.tag = tag; this.className = ''; this.textContent = '';
    this.children = []; this.listeners = {};
    this.classList = { toggle: () => {} };
  }
  append(...children) { for (const child of children) { this.children.push(child); child.parent = this; } }
  remove() { if (this.parent) this.parent.children = this.parent.children.filter(n => n !== this); }
  setAttribute(name, value) { (this.attributes ||= {})[name] = value; }
  addEventListener(name, callback) { this.listeners[name] = callback; }
}
const descendants = node => [node, ...node.children.flatMap(descendants)];
const byClass = (root, name) => descendants(root).filter(node => node.className === name);

function fixture(kind = 'cquadre', count = 3, fail = false) {
  const original = globalThis.document;
  globalThis.document = { createElement: tag => new Element(tag) };
  const items = Array.from({ length: count }, (_, i) => ({
    id: i + 1, titulo: `Notícia ${i + 1}`,
    enlace: `https://example.org/${i + 1}`, fecha: '2026-10-15',
    llegit: false,
  }));
  const saved = new Set(), counts = [];
  const render = () => renderPendingSection({
    kind, title: kind, items: items.map(item => ({ ...item, llegit: saved.has(item.id) })),
    markReadItem: async id => { if (fail) throw Error('denied'); saved.add(id); },
    onUnreadChange: (_, count) => counts.push(count),
    allUrl: 'https://example.org/', allLabel: 'Veure tots',
  });
  return { render, saved, counts, restore() { globalThis.document = original; } };
}

for (const kind of ['cquadre', 'avisos']) {
  test(`obrir un document de ${kind} el marca llegit i persisteix el comptador`, async () => {
    const t = fixture(kind);
    try {
      const section = t.render();
      const link = byClass(section, 'cquadre-link')[0];
      assert.equal(link.target, '_blank');
      assert.equal(link.href, 'https://example.org/1');
      await link.listeners.click();
      assert.deepEqual([...t.saved], [1]);
      assert.deepEqual(t.counts, [2]);
      assert.equal(byClass(section, 'cquadre-item').length, 2);
      assert.equal(byClass(t.render(), 'cquadre-item').length, 2);
      assert.equal(byClass(section, 'cquadre-toolbar')[0].hidden, true);
    } finally { t.restore(); }
  });
}

test('si falla la lectura en obrir, conserva el pendent i informa', async () => {
  const t = fixture('avisos', 3, true);
  try {
    const section = t.render();
    await byClass(section, 'cquadre-link')[0].listeners.click();
    assert.equal(t.saved.size, 0);
    assert.equal(byClass(section, 'cquadre-item').length, 3);
    assert.deepEqual(t.counts, []);
    assert.match(byClass(section, 'cquadre-status')[0].textContent, /No s’ha pogut/);
  } finally { t.restore(); }
});

test('obrir diversos documents durant un marcatge no perd lectures', async () => {
  const t = fixture();
  try {
    const section = t.render();
    byClass(section, 'cquadre-left-action')[0].listeners.click();
    const bulk = byClass(section, 'button cquadre-mark-selected')[0].listeners.click();
    const links = byClass(section, 'cquadre-link');
    await Promise.all([bulk, links[0].listeners.click(), links[1].listeners.click()]);
    assert.deepEqual([...t.saved], [1, 2]);
    assert.deepEqual(t.counts, [2, 1]);
  } finally { t.restore(); }
});

test('el botó central marca llegit; el dret no', async () => {
  const t = fixture();
  try {
    const section = t.render();
    const link = byClass(section, 'cquadre-link')[0];
    await link.listeners.auxclick({ button: 2 });
    assert.equal(t.saved.size, 0);
    await link.listeners.auxclick({ button: 1 });
    assert.deepEqual([...t.saved], [1]);
  } finally { t.restore(); }
});

test('la selecció és directa i el botó de llegits només apareix quan hi ha selecció', async () => {
  const t = fixture();
  try {
    const section = t.render();
    const toolbar = byClass(section, 'cquadre-toolbar')[0];
    const actions = byClass(section, 'cquadre-left-action');
    const mark = byClass(section, 'button cquadre-mark-selected')[0];

    assert.equal(byClass(section, 'cquadre-mode-toggle').length, 0);
    assert.equal(toolbar.hidden, true);

    actions[0].listeners.click();
    assert.equal(toolbar.hidden, false);
    assert.equal(actions[0].attributes['aria-pressed'], 'true');
    assert.equal(mark.textContent, 'Marca com a llegits');

    actions[2].listeners.click();
    assert.equal(t.saved.size, 0);

    await mark.listeners.click();
    assert.deepEqual([...t.saved], [1, 3]);
    assert.equal(byClass(section, 'cquadre-item').length, 1);
    assert.equal(toolbar.hidden, true);
    assert.deepEqual(t.counts, [2, 1]);
  } finally { t.restore(); }
});

test('Avisos permet seleccionar diversos elements sense mode especial', async () => {
  const t = fixture('avisos', 4);
  try {
    const section = t.render();
    const actions = byClass(section, 'cquadre-left-action');
    actions[0].listeners.click();
    actions[1].listeners.click();
    actions[2].listeners.click();

    await byClass(section, 'button cquadre-mark-selected')[0].listeners.click();
    assert.deepEqual([...t.saved], [1, 2, 3]);
    assert.equal(byClass(section, 'cquadre-item').length, 1);
    assert.equal(byClass(t.render(), 'cquadre-item').length, 1);
  } finally { t.restore(); }
});

test('si falla el marcatge, els elements continuen pendents i seleccionables', async () => {
  const t = fixture('avisos', 3, true);
  try {
    const section = t.render();
    byClass(section, 'cquadre-left-action')[0].listeners.click();
    const toolbar = byClass(section, 'cquadre-toolbar')[0];
    assert.equal(toolbar.hidden, false);

    await byClass(section, 'button cquadre-mark-selected')[0].listeners.click();
    assert.equal(byClass(section, 'cquadre-item').length, 3);
    assert.equal(t.saved.size, 0);
    assert.equal(toolbar.hidden, false);
    assert.match(byClass(section, 'cquadre-status')[0].textContent, /No s’han pogut marcar/);
  } finally { t.restore(); }
});
