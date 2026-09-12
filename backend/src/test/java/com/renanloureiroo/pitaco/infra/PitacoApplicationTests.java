package com.renanloureiroo.pitaco.infra;

import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import org.junit.jupiter.api.Test;

// @E2E e não um @SpringBootTest próprio: o mesmo contexto dos demais, já em cache.
@E2E
class PitacoApplicationTests {

  @Test
  void contextLoads() {}
}
