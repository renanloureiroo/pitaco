package com.renanloureiroo.pitaco.testsupport.database;

import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;

// Um truncate com cascade limpa o schema inteiro sem que o teste precise conhecer a ordem das
// chaves estrangeiras — a lista de tabelas vem do próprio banco, então tabelas novas entram de
// graça.
@TestComponent
public class DatabaseCleaner {

  private static final String TABLES_QUERY =
      """
      select table_name
        from information_schema.tables
       where table_schema = current_schema()
         and table_type = 'BASE TABLE'
         and table_name <> 'flyway_schema_history'
      """;

  private final JdbcClient jdbc;

  private List<String> tables;

  DatabaseCleaner(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void clean() {
    if (tables == null) {
      tables = jdbc.sql(TABLES_QUERY).query(String.class).list();
    }

    if (tables.isEmpty()) {
      return;
    }

    jdbc.sql("truncate table " + String.join(", ", tables) + " restart identity cascade").update();
  }
}
