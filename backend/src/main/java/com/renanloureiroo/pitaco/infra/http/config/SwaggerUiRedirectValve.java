package com.renanloureiroo.pitaco.infra.http.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

// Na engine, e não um controller: a raiz do host fica fora do context-path e nunca chega ao
// DispatcherServlet. Aqui também nada passa pelo limite de requisições nem pelos interceptors de
// chave. O Location é relativo: atrás do túnel, o navegador o resolve contra o host e o esquema
// externos, sem depender dos cabeçalhos X-Forwarded-*.
public class SwaggerUiRedirectValve extends ValveBase {

  private final Set<String> entryPoints;
  private final String location;

  public SwaggerUiRedirectValve(String contextPath, String uiPath) {
    super(true);
    var base = contextPath == null ? "" : contextPath.replaceAll("/+$", "");
    this.entryPoints = base.isEmpty() ? Set.of("/") : Set.of("/", base, base + "/");
    this.location = base + (uiPath.startsWith("/") ? uiPath : "/" + uiPath);
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    if (redirects(request.getMethod(), request.getDecodedRequestURI())) {
      response.setStatus(HttpServletResponse.SC_FOUND);
      response.setHeader("Location", location);
      return;
    }

    getNext().invoke(request, response);
  }

  boolean redirects(String method, String path) {
    return ("GET".equals(method) || "HEAD".equals(method)) && entryPoints.contains(path);
  }

  String location() {
    return location;
  }
}
