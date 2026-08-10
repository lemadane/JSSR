package com.jssr.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class RealBootJarIntegrationTest {

    private static Process bootProcess;
    private static final int PORT = 8088;
    private static final String APP_URL = "http://localhost:" + PORT + "/test-render";

    @BeforeAll
    static void startFatJarProcess() throws Exception {
        File jarFile = new File("integration-tests/boot-jar/build/libs/jssr-boot-test.jar");
        assertTrue(jarFile.exists(), "Boot JAR must be built prior to running RealBootJarIntegrationTest. File missing at: " + jarFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-Dserver.port=" + PORT,
                "-jar",
                jarFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        bootProcess = pb.start();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(APP_URL))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        boolean healthy = false;
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    healthy = true;
                    break;
                }
            } catch (Exception ignored) {
                Thread.sleep(500);
            }
        }
        assertTrue(healthy, "Spring Boot fat JAR failed to start and respond on port " + PORT + " within 20s");
    }

    @Test
    @DisplayName("Verify bytecode compilation under genuine java -jar Spring Boot fat JAR executable environment")
    void testExecutableJarPrecompilation() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(APP_URL)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        String body = response.body();

        assertTrue(body.contains("<h1>Alice</h1>"), "Response HTML must contain rendered component header");
        assertTrue(body.contains("<span>Active</span>"), "Response HTML must contain rendered component condition");
        assertTrue(body.contains("\"status\":\"COMPILED\""),
                "Precompiler status must be COMPILED under genuine java -jar LaunchedURLClassLoader, but got: " + body);
    }

    @AfterAll
    static void stopFatJarProcess() {
        if (bootProcess != null && bootProcess.isAlive()) {
            bootProcess.destroyForcibly();
        }
    }
}
