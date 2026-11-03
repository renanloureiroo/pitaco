package com.renanloureiroo.pitaco.modules.results.application.outputs;

import java.util.List;

public record AttributeCatalogOutput(String name, List<ValueCount> values) {

  public record ValueCount(String value, long count) {}
}
