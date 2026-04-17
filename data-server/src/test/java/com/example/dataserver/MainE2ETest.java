package com.example.dataserver;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class MainE2ETest {

    private static int serverPort;
    private static final int STARTUP_TIMEOUT_MS = 5000;

    @BeforeClass
    public static void startServer() throws Exception {
        serverPort = findFreePort();
        String dataFilePath = MainE2ETest.class.getClassLoader()
                .getResource("test-internet-mock.json")
                .getPath();
        DataServerConfig config = DataServerConfig.fromArgs(new String[]{String.valueOf(serverPort), dataFilePath});
        DataServer server = new DataServer(config);
        Thread serverThread = new Thread(server::start, "test-data-server");
        serverThread.setDaemon(true);
        serverThread.start();
        waitForServer(serverPort, STARTUP_TIMEOUT_MS);
    }

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Test
    public void testServerRespondsWithAllFields() throws Exception {
        String response = sendRequest("GET google.com");
        assertTrue(response.contains("NAME: Google"));
        assertTrue(response.contains("LINKS:"));
        assertTrue(response.contains("CONTENT: The search engine"));
    }

    @Test
    public void testServerReturnsLinksForKnownPage() throws Exception {
        String response = sendRequest("GET google.com");
        assertTrue(response.contains("gmail.com"));
        assertTrue(response.contains("youtube.com"));
    }

    @Test
    public void testGetWithLeadingSlashResolvesToSamePage() throws Exception {
        String withSlash = sendRequest("GET /google.com");
        String withoutSlash = sendRequest("GET google.com");
        assertEquals(withoutSlash.trim(), withSlash.trim());
    }

    @Test
    public void testRawUrlWithoutGetPrefixIsResolved() throws Exception {
        String response = sendRequest("google.com");
        assertTrue(response.contains("NAME: Google"));
    }

    @Test
    public void testRawUrlWithLeadingSlashIsResolved() throws Exception {
        String response = sendRequest("/google.com");
        assertTrue(response.contains("NAME: Google"));
    }

    // ─── Error cases ──────────────────────────────────────────────────────────

    @Test
    public void testUnknownUrlReturnsNotFoundError() throws Exception {
        String response = sendRequest("GET this-url-does-not-exist.com");
        assertEquals("ERROR: URL_NOT_FOUND", response.trim());
    }

    @Test
    public void testEmptyRequestReturnsEmptyRequestError() throws Exception {
        String response = sendRequest("");
        assertEquals("ERROR: EMPTY_REQUEST", response.trim());
    }

    @Test
    public void testBlankRequestReturnsEmptyRequestError() throws Exception {
        String response = sendRequest("   ");
        assertEquals("ERROR: EMPTY_REQUEST", response.trim());
    }

    // ─── Concurrency ──────────────────────────────────────────────────────────

    @Test
    public void testServerHandlesConcurrentRequests() throws Exception {
        int numRequests = 10;
        ExecutorService pool = Executors.newFixedThreadPool(numRequests);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < numRequests; i++) {
            futures.add(pool.submit(() -> sendRequest("GET google.com")));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<String> future : futures) {
            assertTrue(future.get().contains("LINKS:"));
        }
    }

    // ─── Main argument validation ─────────────────────────────────────────────

    @Test
    public void testMainExitsWithCode1OnNoArgs() throws Exception {
        Process process = new ProcessBuilder(
                "java", "-cp", System.getProperty("java.class.path"),
                "com.example.dataserver.Main"
        ).redirectErrorStream(true).start();
        process.getInputStream().transferTo(OutputStream.nullOutputStream());
        assertEquals(1, process.waitFor());
    }

    @Test
    public void testMainExitsWithCode1OnUnknownMode() throws Exception {
        Process process = new ProcessBuilder(
                "java", "-cp", System.getProperty("java.class.path"),
                "com.example.dataserver.Main", "--unknown"
        ).redirectErrorStream(true).start();
        process.getInputStream().transferTo(OutputStream.nullOutputStream());
        assertEquals(1, process.waitFor());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String sendRequest(String request) throws IOException {
        try (Socket socket = new Socket("localhost", serverPort);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.println(request);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void waitForServer(int port, int maxWaitMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket s = new Socket("localhost", port)) {
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("Server did not start within " + maxWaitMs + " ms on port " + port);
    }

}
