# Briefing comum da Fase 5 — Projeto de exemplo (sdk/example)

Repositório: /Users/renanloureiro/www/pitaco. SDK em sdk/ (TypeScript, react 19.2, react-native 0.87.1 como devDependency, builder-bob). Exemplo novo em sdk/example/. Português do Brasil com acentuação correta em textos, README e comentários; identificadores em inglês.

## Leitura obrigatória
- /Users/renanloureiro/www/pitaco/docs/prompts/sdk-react-native.md inteiro, com atenção à "Fase 5 — Projeto de exemplo" (estrutura, ligação com o SDK, conexão com o backend, quinze cenários, capturas, painel de depuração, teste do exemplo), às restrições e à superfície pública.
- /private/tmp/claude-501/-Users-renanloureiro-www-pitaco/fda84fe7-4020-48bb-a8d1-04d9e4d5e771/scratchpad/relatorio-fases.md: todas as seções (fases 1 a 4 e as subtarefas da fase 5 já concluídas). Ali está a API real do SDK, os pontos de diagnóstico (runtime.diagnostics(), runtime.simulateAppReopen()), os eventos placement_ e as decisões de apresentação.
- backend/docs/backend/contrato-sdk.md e backend/docs/backend/proxy.md.
- O código real do SDK em sdk/src (a API pública em sdk/src/index.ts e sdk/src/preview/index.ts manda sobre qualquer suposição).
- Regra global do usuário: consulte o Context7 MCP (carregue mcp__context7__resolve-library-id e mcp__context7__query-docs via ToolSearch) antes de configurar Expo, Expo Router, Metro, @gorhom/bottom-sheet, react-native-reanimated, react-native-gesture-handler, react-native-safe-area-context, AsyncStorage e Maestro. Use a versão do Expo SDK mais recente suportada pelo Expo Go instalado/instalável e as versões de dependências que `npx expo install` escolher.

## Restrições
- O SDK continua com zero módulos nativos e só react/react-native como peer. As bibliotecas nativas (reanimated, gesture-handler, gorhom, safe-area-context, async-storage, mmkv) são dependências do EXEMPLO, nunca do SDK.
- O exemplo roda no Expo Go em iOS e Android sem build nativo. Atenção: react-native-mmkv não existe no Expo Go. Decisão já tomada: o exemplo usa AsyncStorage como storage persistente; o adaptador MMKV do SDK fica coberto pelos testes unitários do SDK e mostrado no README como trecho de código; não importe react-native-mmkv em nenhum código que o Expo Go carregue (se instalar como dependência para provar a convivência, que seja sem import em runtime, e registre).
- O exemplo consome o CÓDIGO-FONTE do SDK: metro.config.js com watchFolders apontando para sdk/, @pitaco/react-native resolvido para ../src (incluindo os subpaths /preview e /storage/*), uma única cópia de react e react-native (bloqueie as de sdk/node_modules via blockList/resolver). O tsconfig do exemplo também resolve @pitaco/react-native para ../src.
- Se um cenário revelar defeito no SDK, a correção é no SDK (sdk/src), mínima, com teste, e registrada no relatório. Nunca contorne um defeito do SDK no exemplo. Se exigir módulo nativo, o problema está no SDK.
- Não faça commit. Não mexa no painel nem no CI. Backend: não altere código; só execute.
- Degradação silenciosa vale também para o exemplo como vitrine: nenhuma falha do Pitaco deixa o app inutilizável.

## Portões
- `cd sdk && npm run verify` continua verde. O verify do SDK passa a incluir `npm run typecheck` e `npm run lint` do exemplo (sem quebrar o lint/typecheck do SDK por causa dos arquivos do exemplo: exclua sdk/example do eslint e do tsconfig do SDK e do bob).
- Relatório: ao terminar, acrescente ao fim do relatorio-fases.md uma seção "## Fase 5x — <nome>" com arquivos criados/alterados, decisões (com o porquê), o que ficou de fora, defeitos do SDK encontrados e corrigidos, e o resultado exato de cada comando executado. Resposta final: resumo de até 25 linhas com o mesmo conteúdo.
