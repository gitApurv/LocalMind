package com.localmind.tools.service;

import static org.junit.jupiter.api.Assertions.*;

import com.localmind.tools.config.WebScraperProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WebScraperTools}: clean text extraction and truncation, URL scheme checks, connection failures,
 * redirects, non-text content, and the SSRF guard on every hop including restricted ranges and DNS rebinding. Pages are
 * served by a local {@code HttpServer}.
 */
class WebScraperToolsTest {

    private WebScraperTools scraperTools;
    private HttpServer testServer;
    private String serverBaseUrl;
    private int serverPort;
    private final AtomicInteger secretHits = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        scraperTools = new WebScraperTools(new WebScraperProperties(true));

        testServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        testServer.createContext("/article", exchange -> {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Sample Article</title>
                        <style>body { font-family: sans-serif; }</style>
                        <script>console.log("tracking");</script>
                    </head>
                    <body>
                        <header><nav><a href="/">Home</a></nav></header>
                        <main>
                            <h1>LocalMind AI Release</h1>
                            <p>LocalMind is an open-source, privacy-first conversational AI platform.</p>
                        </main>
                        <footer>Copyright 2026</footer>
                    </body>
                    </html>
                    """;
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        testServer.createContext("/huge", exchange -> {
            StringBuilder huge = new StringBuilder("<!DOCTYPE html><html><body><h1>Huge Article</h1><p>");
            huge.append("Data excerpt sentence. ".repeat(400));
            huge.append("</p></body></html>");

            byte[] bytes = huge.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        testServer.createContext("/secret", exchange -> {
            secretHits.incrementAndGet();
            byte[] bytes = "<html><body>internal secret</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        testServer.createContext("/document.pdf", exchange -> {
            byte[] bytes = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        testServer.createContext("/redirect-to-article", exchange -> redirect(exchange, "/article"));
        testServer.createContext("/redirect-to-private",
                exchange -> redirect(exchange, "http://10.0.0.1/admin"));
        testServer.createContext("/redirect-loop", exchange -> redirect(exchange, "/redirect-loop"));
        testServer.start();
        serverPort = testServer.getAddress().getPort();
        serverBaseUrl = "http://127.0.0.1:" + serverPort;
    }

    @AfterEach
    void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @Test
    @DisplayName("readWebPage() should extract clean text without scripts, styles, nav or footer")
    void scrapeHtmlSuccess() {
        String content = scraperTools.readWebPage(serverBaseUrl + "/article");
        assertNotNull(content);
        assertTrue(content.contains("LocalMind AI Release"));
        assertTrue(content.contains("LocalMind is an open-source, privacy-first conversational AI platform."));
        assertFalse(content.contains("console.log"));
        assertFalse(content.contains("font-family"));
        assertFalse(content.contains("Copyright 2026"));
    }

    @Test
    @DisplayName("readWebPage() should truncate content exceeding 4,000 characters")
    void truncateLargePageContent() {
        String content = scraperTools.readWebPage(serverBaseUrl + "/huge");
        assertNotNull(content);
        assertTrue(content.contains("[Content truncated to 4,000 characters]"));
        assertTrue(content.length() <= 4100);
    }

    @Test
    @DisplayName("readWebPage() should allow uppercase HTTP/HTTPS schemes")
    void allowUppercaseHttpScheme() {
        String upperResult = scraperTools.readWebPage(serverBaseUrl.toUpperCase(java.util.Locale.ROOT) + "/article");
        assertNotNull(upperResult);
        assertTrue(upperResult.contains("LocalMind AI Release"));
    }

    @Test
    @DisplayName("readWebPage() should reject non-HTTP/HTTPS URLs")
    void rejectInvalidUrlSchemes() {
        String fileResult = scraperTools.readWebPage("file:///etc/passwd");
        assertTrue(fileResult.toLowerCase().contains("error") || fileResult.toLowerCase().contains("invalid"));

        String ftpResult = scraperTools.readWebPage("ftp://ftp.example.com/file");
        assertTrue(ftpResult.toLowerCase().contains("error") || ftpResult.toLowerCase().contains("invalid"));

        String nullResult = scraperTools.readWebPage(null);
        assertTrue(nullResult.toLowerCase().contains("error") || nullResult.toLowerCase().contains("blank"));
    }

    @Test
    @DisplayName("readWebPage() should handle connection failures gracefully")
    void handleConnectionFailure() {
        String failResult = scraperTools.readWebPage("http://127.0.0.1:59999/nonexistent");
        assertNotNull(failResult);
        assertTrue(failResult.toLowerCase().contains("error"));
    }

    @Test
    @DisplayName("FunctionCallback invocation for WebScraperTools should return extracted text")
    void functionCallbackExecution() {
        var callback = scraperTools.toolCallback();
        assertNotNull(callback);
        assertEquals("readWebPage", callback.getToolDefinition().name());

        String jsonPayload = "{\"url\": \"" + serverBaseUrl + "/article\"}";
        String response = callback.call(jsonPayload);
        assertNotNull(response);
        assertTrue(response.contains("LocalMind AI Release"));
    }

    @Test
    @DisplayName("readWebPage() should block private and loopback IP addresses when SSRF guard is active")
    void ssrfDefenseBlocksPrivateAndLoopbackAddresses() {
        WebScraperTools guardedScraper = new WebScraperTools(new WebScraperProperties(false));

        String loopbackIp = guardedScraper.readWebPage("http://127.0.0.1:8080/internal");
        assertTrue(loopbackIp.contains("SSRF protection"));

        String localhost = guardedScraper.readWebPage("http://localhost:3306");
        assertTrue(localhost.contains("SSRF protection"));

        String privateIp10 = guardedScraper.readWebPage("http://10.0.0.1/admin");
        assertTrue(privateIp10.contains("SSRF protection"));

        String privateIp192 = guardedScraper.readWebPage("http://192.168.1.1/router");
        assertTrue(privateIp192.contains("SSRF protection"));

        String privateIp172 = guardedScraper.readWebPage("http://172.16.0.1/status");
        assertTrue(privateIp172.contains("SSRF protection"));

        String linkLocal = guardedScraper.readWebPage("http://169.254.169.254/latest/meta-data");
        assertTrue(linkLocal.contains("SSRF protection"));
    }

    @Test
    @DisplayName("readWebPage() should follow redirects and return the final page")
    void followsRedirects() {
        String content = scraperTools.readWebPage(serverBaseUrl + "/redirect-to-article");
        assertTrue(content.contains("LocalMind AI Release"));
    }

    @Test
    @DisplayName("readWebPage() should block a redirect that points into a private network")
    void blocksRedirectToPrivateNetwork() {
        WebScraperTools guarded = new WebScraperTools(new WebScraperProperties(false),
                address -> !address.isLoopbackAddress() && WebScraperTools.isRestrictedAddress(address));

        String result = guarded.readWebPage(serverBaseUrl + "/redirect-to-private");

        assertTrue(result.contains("SSRF protection"));
    }

    @Test
    @DisplayName("readWebPage() should stop after too many redirects")
    void stopsRedirectLoops() {
        String result = scraperTools.readWebPage(serverBaseUrl + "/redirect-loop");
        assertTrue(result.contains("Too many redirects"));
    }

    @Test
    @DisplayName("isRestrictedAddress() should cover CGNAT, IPv6 unique-local, unspecified and multicast ranges")
    void restrictsAdditionalPrivateRanges() throws Exception {
        assertTrue(WebScraperTools.isRestrictedAddress(java.net.InetAddress.getByName("100.64.0.1")));
        assertTrue(WebScraperTools.isRestrictedAddress(java.net.InetAddress.getByName("fd12:3456::1")));
        assertTrue(WebScraperTools.isRestrictedAddress(java.net.InetAddress.getByName("0.0.0.0")));
        assertTrue(WebScraperTools.isRestrictedAddress(java.net.InetAddress.getByName("224.0.0.1")));
        assertTrue(WebScraperTools.isRestrictedAddress(java.net.InetAddress.getByName("::ffff:10.0.0.1")));
        assertFalse(WebScraperTools.isRestrictedAddress(java.net.InetAddress.getByName("93.184.216.34")));
    }

    @Test
    @DisplayName("readWebPage() should connect only to the addresses its SSRF check approved (DNS rebinding)")
    void connectsOnlyToCheckedAddresses() {
        AtomicInteger lookups = new AtomicInteger();
        WebScraperTools guarded = new WebScraperTools(new WebScraperProperties(false),
                address -> address.getHostAddress().equals("127.0.0.1"),
                host -> lookups.incrementAndGet() == 1
                        ? new InetAddress[]{InetAddress.getByName("::1")}
                        : new InetAddress[]{InetAddress.getByName("127.0.0.1")});

        String result = guarded.readWebPage("http://rebind.test:" + serverPort + "/secret");

        assertEquals(1, lookups.get(), "the checked lookup must be the only one");
        assertEquals(0, secretHits.get(), "the private target must never be reached");
        assertFalse(result.contains("internal secret"));
    }

    @Test
    @DisplayName("readWebPage() should block a host when any of its addresses is private")
    void blocksHostWithAnyPrivateAddress() {
        WebScraperTools guarded = new WebScraperTools(new WebScraperProperties(false),
                WebScraperTools::isRestrictedAddress, host -> new InetAddress[]{
                        InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.1")});

        assertTrue(guarded.readWebPage("http://mixed.test/page").contains("SSRF protection"));
    }

    @Test
    @DisplayName("readWebPage() should connect through the guarded resolver's answer, not a second DNS lookup")
    void connectsThroughGuardedResolver() {
        WebScraperTools guarded = new WebScraperTools(new WebScraperProperties(false),
                address -> !address.isLoopbackAddress() && WebScraperTools.isRestrictedAddress(address),
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")});

        String content = guarded.readWebPage("http://scraper.test:" + serverPort + "/article");

        assertTrue(content.contains("LocalMind AI Release"));
    }

    @Test
    @DisplayName("readWebPage() should report non-text content such as PDF instead of parsing it")
    void rejectsNonTextContent() {
        String result = scraperTools.readWebPage(serverBaseUrl + "/document.pdf");

        assertTrue(result.contains("not a readable text or HTML page"));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} restricted = {1}")
    @org.junit.jupiter.params.provider.CsvSource({"192.0.0.8, true", "198.18.0.1, true", "198.19.255.255, true",
            "240.0.0.1, true", "255.255.255.255, true", "64:ff9b::a00:1, true", "64:ff9b::808:808, false",
            "64:ff9b:1::1, true", "2002:a00:1::1, true", "2002:808:808::1, false", "2001:0:4136:e378::1, true",
            "::a00:1, true", "::ffff:808:808, false", "198.20.0.1, false", "192.0.2.1, false",
            "2606:4700::1111, false"})
    @DisplayName("isRestrictedAddress() should cover special-purpose IPv4 ranges and IPv6 forms that embed IPv4")
    void restrictsSpecialPurposeAndEmbeddedRanges(String address, boolean restricted) throws Exception {
        assertEquals(restricted, WebScraperTools.isRestrictedAddress(InetAddress.getByName(address)));
    }

    private static void redirect(com.sun.net.httpserver.HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
