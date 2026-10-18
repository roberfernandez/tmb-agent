import test from 'node:test';
import assert from 'node:assert/strict';
import {permits,filterPermits} from '../src/permisos-data.js';
import {cases,filterCases,guides} from '../src/tmobilitat-data.js';
import {highlightedParts} from '../src/text-search.js';
test('search indexes pending and disputed conditions without changing their text',()=>{for(const p of permits)for(const key of ['pending','controversy'])if(p[key])assert.ok(filterPermits({query:p[key]}).some(r=>r.id===p.id));});
test('T-mobilitat searches full original and guide branches, preserving case membership',()=>{for(const c of cases){if(c.original)assert.ok(filterCases({query:c.original}).some(r=>r.id===c.id));if(c.guide)for(const n of Object.values(guides[c.guide].nodes)){const text=n.question||n.result;if(text)assert.ok(filterCases({query:text}).some(r=>r.id===c.id));}}});
test('highlights keep exact accents and HTML-like source text as plain data',()=>{const text='Hospitalització <suport> i qüestions';const p=highlightedParts(text,'hospitalitzacio suport');assert.equal(p.map(x=>x.text).join(''),text);assert.deepEqual(p.filter(x=>x.match).map(x=>x.text),['Hospitalització','suport']);});
