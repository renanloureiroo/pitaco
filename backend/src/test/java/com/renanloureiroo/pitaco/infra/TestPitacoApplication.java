package com.renanloureiroo.pitaco.infra;

import org.springframework.boot.SpringApplication;

public class TestPitacoApplication {

	public static void main(String[] args) {
		SpringApplication.from(PitacoApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
