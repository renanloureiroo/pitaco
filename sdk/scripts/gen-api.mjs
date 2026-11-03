// Regenera os tipos do contrato a partir do OpenAPI do backend.
//
// 1. Tenta baixar o documento de um backend local (PITACO_OPENAPI_URL, padrão
//    http://localhost:8080/api/v3/api-docs) e, se conseguir, atualiza o snapshot em
//    openapi/pitaco.json.
// 2. Sem backend no ar, usa o snapshot já versionado: o build nunca depende da API.
// 3. Gera src/api/openapi.ts com o openapi-typescript.
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const snapshot = resolve(root, 'openapi/pitaco.json');
const output = resolve(root, 'src/api/openapi.ts');
const url = process.env.PITACO_OPENAPI_URL ?? 'http://localhost:8080/api/v3/api-docs';

async function download() {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(5000) });
    if (!response.ok) {
      console.warn(`[gen:api] ${url} respondeu ${response.status}; mantendo o snapshot.`);
      return;
    }
    const document = await response.json();
    // `servers` muda com a porta local e só polui o diff do snapshot.
    delete document.servers;
    writeFileSync(snapshot, `${JSON.stringify(document, null, 2)}\n`);
    console.log(`[gen:api] snapshot atualizado a partir de ${url}.`);
  } catch {
    console.warn(`[gen:api] backend indisponível em ${url}; usando o snapshot versionado.`);
  }
}

await download();
JSON.parse(readFileSync(snapshot, 'utf8'));
execFileSync(
  resolve(root, 'node_modules/.bin/openapi-typescript'),
  [snapshot, '-o', output],
  { stdio: 'inherit' },
);
const generated = readFileSync(output, 'utf8');
writeFileSync(
  output,
  `/* eslint-disable */\n// Gerado por \`npm run gen:api\` a partir de openapi/pitaco.json. Não edite à mão.\n${generated}`,
);
