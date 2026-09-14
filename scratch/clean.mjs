import fs from 'node:fs';
const env = fs.readFileSync('sdk/example/.env', 'utf8');
const key = env.match(/EXPO_PUBLIC_PITACO_API_KEY=(.+)/)[1];

async function run() {
  const res = await fetch('https://pitaco.renanloureiro.me/api/applications');
  const apps = (await res.json()).items;
  const demo = apps.find(a => a.slug === 'demo');
  if (!demo) return;
  const sRes = await fetch(`https://pitaco.renanloureiro.me/api/applications/${demo.id}/surveys`);
  const surveys = (await sRes.json()).items;
  for (const s of surveys) {
    if (s.name !== 'Pesquisa de exemplo') {
      const del = await fetch(`https://pitaco.renanloureiro.me/api/applications/${demo.id}/surveys/${s.id}`, { method: 'DELETE' });
      console.log('Deleted', s.name, del.status);
    }
  }
}
run();
