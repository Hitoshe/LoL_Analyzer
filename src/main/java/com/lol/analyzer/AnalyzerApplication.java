package com.lol.analyzer;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point. Loads optional project-root {@code .env} into system properties
 * so {@code RIOT_API_KEY} / {@code GEMINI_API_KEY} resolve before the Spring Environment is built.
 * Docker Compose can inject the same variables via {@code env_file: .env} instead.
 */
@SpringBootApplication
public class AnalyzerApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().directory("./").ignoreIfMissing().load();
		dotenv.entries().forEach(e -> {
			String key = e.getKey();
			if (System.getenv(key) == null && System.getProperty(key) == null) {
				System.setProperty(key, e.getValue());
			}
		});
		SpringApplication.run(AnalyzerApplication.class, args);
	}

}
