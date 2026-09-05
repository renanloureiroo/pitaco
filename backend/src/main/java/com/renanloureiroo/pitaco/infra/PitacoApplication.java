package com.renanloureiroo.pitaco.infra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.renanloureiroo.pitaco")
@EntityScan("com.renanloureiroo.pitaco")
@EnableJpaRepositories("com.renanloureiroo.pitaco")
public class PitacoApplication {

  public static void main(String[] args) {
    SpringApplication.run(PitacoApplication.class, args);
  }
}
