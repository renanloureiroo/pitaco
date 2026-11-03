import { test as base, type Page } from "@playwright/test";

export { expect } from "@playwright/test";
export type { APIRequestContext, Locator, Page } from "@playwright/test";

/**
 * Espera o streaming do SSR assentar depois de uma carga completa de documento.
 *
 * Com Suspense (os `loading.tsx` das rotas), o React manda o conteúdo resolvido dentro de um
 * `<div hidden id="S:n">` e só depois o move para o lugar com um script de revelação, que o
 * React 19.2 agrupa e atrasa de propósito. Nessa janela o mesmo conteúdo existe duas vezes no
 * DOM — e `getByTestId`, que não filtra por visibilidade, acha dois elementos. Uma navegação no
 * cliente não passa por isso: só `goto` e `reload` carregam o documento do zero.
 *
 * Com o documento carregado, todos os scripts de conclusão já rodaram e enfileiraram suas
 * revelações em `$RB`, a fila do runtime do React; fila vazia é conteúdo no lugar. O `<div
 * hidden>` em si não serve de sinal: numa tela de não encontrado sobra um que nunca é revelado.
 */
async function settleStreaming(page: Page): Promise<void> {
  await page.waitForFunction(() => {
    const pending = (window as Window & { $RB?: unknown[] }).$RB;
    return document.readyState === "complete" && (pending === undefined || pending.length === 0);
  });
}

export const test = base.extend({
  page: async ({ page }, provide) => {
    const goto = page.goto.bind(page);
    const reload = page.reload.bind(page);

    page.goto = async (...args: Parameters<Page["goto"]>) => {
      const response = await goto(...args);
      await settleStreaming(page);
      return response;
    };
    page.reload = async (...args: Parameters<Page["reload"]>) => {
      const response = await reload(...args);
      await settleStreaming(page);
      return response;
    };

    await provide(page);
  },
});
