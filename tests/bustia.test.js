import { test } from 'node:test';
import assert from 'node:assert/strict';
import { availableModules, categoryLabel, statusLabel } from '../src/bustia.js';

test('Bustia translates known proposal categories', () => {
  assert.equal(categoryLabel('millora'), 'Millora d’una miniapp');
  assert.equal(categoryLabel('nova_miniapp'), 'Nova miniapp');
  assert.equal(categoryLabel('nova_funcio'), 'Nova funció');
  assert.equal(categoryLabel('altres'), 'Altres');
});

test('Bustia translates workflow states', () => {
  assert.equal(statusLabel('oberta'), 'Oberta');
  assert.equal(statusLabel('en_estudi'), 'En estudi');
  assert.equal(statusLabel('acceptada'), 'Acceptada');
  assert.equal(statusLabel('en_desenvolupament'), 'En desenvolupament');
  assert.equal(statusLabel('feta'), 'Feta');
  assert.equal(statusLabel('descartada'), 'Descartada');
});

test('module picker excludes Bustia itself', () => {
  const ids = availableModules().map(module => module.id);
  assert.ok(ids.includes('cquadre'));
  assert.ok(ids.includes('incidencias'));
  assert.ok(!ids.includes('bustia'));
});
