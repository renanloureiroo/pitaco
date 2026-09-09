package com.renanloureiroo.pitaco.infra.http.security;

// A aplicação derivada da chave. Nenhuma operação pública aceita applicationId como parâmetro
// de rota ou de corpo (FR-002).
public record AuthenticatedApplication(String applicationId, boolean active) {}
