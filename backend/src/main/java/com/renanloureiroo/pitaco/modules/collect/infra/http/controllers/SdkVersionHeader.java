package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

// Cabeçalho e não campo de corpo: a mesma versão vale para toda chamada do SDK, e o proxy do app
// hospedeiro repassa cabeçalho sem precisar entender o corpo.
public final class SdkVersionHeader {

  public static final String NAME = "X-Pitaco-Sdk-Version";

  private SdkVersionHeader() {}
}
