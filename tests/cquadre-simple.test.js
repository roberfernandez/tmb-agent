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
test('one left control marks an individual complement immediately; no right control', async () => {
  const t = fixture();
  try {
    const section = t.render();
    assert.equal(byClass(section, 'cquadre-left-action').length, 3);
    assert.equal(byClass(section, 'cquadre-read-button').length, 0);
    assert.equal(byClass(section, 'cquadre-link').length, 3);
    assert.equal(byClass(section, 'cquadre-bulk-tools')[0].hidden, true);
    await byClass(section, 'cquadre-left-action')[0].listeners.click();
    assert.deepEqual([...t.saved], [1]);
    assert.equal(byClass(section, 'cquadre-item').length, 2);
    assert.deepEqual(t.counts, [2]);
    assert.equal(byClass(t.render(), 'cquadre-item').length, 2);
  } finally { t.restore(); }
});
test('multi selection does not save until confirmed and can select just two complements', async () => {
  const t = fixture();
  try {
    const section = t.render();
    byClass(section, 'cquadre-mode-toggle')[0].listeners.click();
    assert.equal(byClass(section, 'cquadre-bulk-tools')[0].hidden, false);
    const actions = byClass(section, 'cquadre-left-action');
    await actions[0].listeners.click(); await actions[2].listeners.click();
    assert.equal(t.saved.size, 0);
    assert.match(byClass(section, 'button cquadre-mark-selected')[0].textContent, /\(2\)/);
    await byClass(section, 'button cquadre-mark-selected')[0].listeners.click();
    assert.deepEqual([...t.saved], [1, 3]);
    assert.equal(byClass(section, 'cquadre-item').length, 1);
    assert.equal(byClass(section, 'cquadre-bulk-tools')[0].hidden, true);
    await byClass(section, 'cquadre-left-action')[0].listeners.click();
    assert.equal(t.saved.size, 3);
    assert.equal(byClass(section, 'cquadre-item').length, 0);
  } finally { t.restore(); }
});
test('Avisos multi selection select-all, deselect one, and retain one as unread after reload', async () => {
  const t = fixture('avisos', 4);
  try {
    const section = t.render();
    byClass(section, 'cquadre-mode-toggle')[0].listeners.click();
    byClass(section, 'cquadre-select-all')[0].listeners.click();
    await byClass(section, 'cquadre-left-action')[3].listeners.click();
    assert.equal(t.saved.size, 0);
    await byClass(section, 'button cquadre-mark-selected')[0].listeners.click();
    assert.deepEqual([...t.saved], [1, 2, 3]);
    assert.equal(byClass(section, 'cquadre-item').length, 1);
    assert.equal(byClass(t.render(), 'cquadre-item').length, 1);
    assert.deepEqual(t.counts, [3, 2, 1]);
  } finally { t.restore(); }
});
test('failed read retains the item and its unread count', async () => {
  const t = fixture('avisos', 3, true);
  try {
    const section = t.render();
    await byClass(section, 'cquadre-left-action')[0].listeners.click();
    assert.equal(byClass(section, 'cquadre-item').length, 3);
    assert.equal(t.saved.size, 0);
    assert.match(byClass(section, 'cquadre-status')[0].textContent, /No s’ha pogut/);
  } finally { t.restore(); }
});
