package com.renanloureiroo.pitaco.infra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.renanloureiroo.pitaco")
public class PitacoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PitacoApplication.class, args);
	}

}
