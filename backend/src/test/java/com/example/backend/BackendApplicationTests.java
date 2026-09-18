package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class BackendApplicationTests {

	@Container
	private static final PostgreSQLContainer<?> postgresql =
			new PostgreSQLContainer<>(
					DockerImageName.parse("pgvector/pgvector:pg16")
							.asCompatibleSubstituteFor("postgres")
			)
					.withDatabaseName("backend_test")
					.withUsername("backend_test")
					.withPassword("backend_test");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgresql::getJdbcUrl);
		registry.add("spring.datasource.username", postgresql::getUsername);
		registry.add("spring.datasource.password", postgresql::getPassword);
		registry.add(
				"app.jwt.secret",
				() -> "test-jwt-secret-for-container-context-tests-32-bytes"
		);
	}

	@Test
	void contextLoads() {
	}

}
