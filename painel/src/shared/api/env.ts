/**
 * Configuração de ambiente do cliente HTTP.
 *
 * `PITACO_API_URL` é lida **somente no servidor**: nenhuma requisição ao backend parte do
 * navegador, então não existe equivalente `NEXT_PUBLIC_*`. A leitura é preguiçosa e falha
 * rápido — na primeira chamada, com mensagem explícita — em vez de montar uma URL relativa
 * que produziria um erro de rede indecifrável (R7).
 */

export const API_URL_ENV_VAR = "PITACO_API_URL";

export class MissingApiUrlError extends Error {
  constructor() {
    super(
      `${API_URL_ENV_VAR} não está definida. Defina-a em .env.local com a URL base da API ` +
        `do Pitaco, incluindo o context-path (ex.: http://localhost:8080/api).`,
    );
    this.name = "MissingApiUrlError";
  }
}

/** Base da API sem barra final, para concatenar com caminhos que começam por barra. */
export function getApiBaseUrl(): string {
  const raw = process.env[API_URL_ENV_VAR];

  if (raw === undefined || raw.trim() === "") {
    throw new MissingApiUrlError();
  }

  return raw.trim().replace(/\/+$/, "");
}
