package com.denizcelikhalat.katalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // Bunu Ekle

@SpringBootApplication(exclude = { FlywayAutoConfiguration.class })
public class KatalogApplication {
	public static void main(String[] args) {
		// Şu iki satırı ekle:
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		System.out.println("====== BENİM HASHİM ======");
		System.out.println(encoder.encode("12345"));
		System.out.println("==========================");

		SpringApplication.run(KatalogApplication.class, args);
	}
}