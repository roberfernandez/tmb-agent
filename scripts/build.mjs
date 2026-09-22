import { mkdir, cp, readFile, writeFile, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
await mkdir('dist', { recursive: true });
await cp('index.html', 'dist/index.html');
await cp('src', 'dist/src', { recursive: true });
await cp('public', 'dist', { recursive: true });
await cp('auth/build/web', 'dist/auth', { recursive: true });
const hash = createHash('sha256');
async function digest(path) {
  for (const entry of (await readdir(path, { withFileTypes: true })).sort((a,b) => a.name.localeCompare(b.name))) {
    const file = `${path}/${entry.name}`;
    if (entry.isDirectory()) await digest(file);
    else { hash.update(file); hash.update(await readFile(file)); }
  }
}
await digest('src'); await digest('public'); hash.update(await readFile('index.html'));
await writeFile('dist/sw.js', (await readFile('public/sw.js', 'utf8')).replace('__VERSION__', hash.digest('hex').slice(0, 12)));
await writeFile('dist/.nojekyll', '');
console.log('TMB Agent: exportación estática preparada en dist/');
