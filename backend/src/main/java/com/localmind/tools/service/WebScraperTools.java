package com.localmind.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.localmind.tools.config.WebScraperProperties;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * LLM tool {@code readWebPage}: lets the model fetch a web page and read its text, cut to 4,000 characters. Requests to
 * loopback and private networks are blocked (SSRF guard) unless explicitly allowed.
 * <p>
 * The guard lives in the HTTP client's DNS resolver ({@link GuardedDnsResolver}, ranges in
 * {@link RestrictedAddresses}): the addresses it validates are the only ones the connection may use. Checking a host
 * and then letting the HTTP library resolve it again would let a DNS server answer the check with a public address and
 * the connection with a private one (DNS rebinding). Redirects are followed manually, so every hop goes through the
 * same resolver.
 */
@Slf4j
@Component
public class WebScraperTools {

    private static final String NAME = "readWebPage";
    private static final String DESCRIPTION = "Fetch and extract readable text content from a web page URL (HTTP/HTTPS). "
            + "Strips extraneous scripts/styles and returns up to 4,000 characters of clean content.";
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final Timeout TIMEOUT = Timeout.ofSeconds(10);
    private static final String USER_AGENT = "LocalMindBot/1.0 (Privacy-first AI Assistant; +https://github.com/gitApurv/LocalMind)";
    private static final String NON_CONTENT_ELEMENTS = "script, style, noscript, nav, footer, header, svg, form";
    private static final String SSRF_BLOCKED = "Error: Access to private or loopback IP addresses is restricted (SSRF protection).";

    private final CloseableHttpClient httpClient;

    @Autowired
    public WebScraperTools(WebScraperProperties properties) {
        this(properties, RestrictedAddresses::isRestricted);
    }

    WebScraperTools(WebScraperProperties properties, Predicate<InetAddress> restrictedAddress) {
        this(properties, restrictedAddress, InetAddress::getAllByName);
    }

    WebScraperTools(WebScraperProperties properties, Predicate<InetAddress> restrictedAddress, HostLookup hostLookup) {
        this.httpClient = buildHttpClient(
                new GuardedDnsResolver(properties.allowPrivateSubnets(), restrictedAddress, hostLookup));
    }

    public record WebScrapeRequest(@JsonProperty(value = "url", required = true) String url) {
    }

    public String readWebPage(String url) {
        if (url == null || url.isBlank()) {
            return "Error: URL must not be null or blank.";
        }
        String currentUrl = url.trim();
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                String rejection = validateTarget(currentUrl);
                if (rejection != null) {
                    return rejection;
                }
                String target = currentUrl;
                Hop result = httpClient.execute(new HttpGet(URI.create(target)), response -> handle(target, response));
                if (result.redirectTo() == null) {
                    return result.text();
                }
                currentUrl = URI.create(currentUrl).resolve(result.redirectTo()).toString();
            }
            return "Error: Too many redirects (more than " + MAX_REDIRECTS + ").";
        } catch (Exception e) {
            return describeFailure(currentUrl, e);
        }
    }

    private record Hop(String redirectTo, String text) {
    }

    private Hop handle(String url, ClassicHttpResponse response) throws IOException {
        int status = response.getCode();
        Header location = response.getFirstHeader("Location");
        if (isRedirect(status) && location != null) {
            return new Hop(location.getValue(), null);
        }
        if (status >= 400) {
            return new Hop(null, "Error fetching web page (" + url + "): HTTP " + status);
        }
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return new Hop(null, "The web page at " + url + " contained no readable text content.");
        }
        ContentType contentType = ContentType.parseLenient(entity.getContentType());
        if (!isTextual(contentType)) {
            return new Hop(null,
                    "Error fetching web page (" + url + "): the content is not a readable text or HTML page.");
        }
        return new Hop(null, extractText(url, parse(url, entity, contentType)));
    }

    private static Document parse(String url, HttpEntity entity, ContentType contentType) throws IOException {
        byte[] body;
        try (InputStream in = entity.getContent()) {
            body = in.readNBytes(MAX_BODY_BYTES);
        }
        Charset charset = contentType != null ? contentType.getCharset() : null;
        String charsetName = charset != null ? charset.name() : null;
        return Jsoup.parse(new ByteArrayInputStream(body), charsetName, url);
    }

    private static boolean isTextual(ContentType contentType) {
        if (contentType == null || contentType.getMimeType() == null) {
            return true;
        }
        String mimeType = contentType.getMimeType().toLowerCase(Locale.ROOT);
        return mimeType.startsWith("text/") || mimeType.equals("application/xml") || mimeType.endsWith("+xml");
    }

    private String describeFailure(String url, Exception e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof BlockedHostException) {
                return SSRF_BLOCKED;
            }
            if (cause instanceof UnknownHostException) {
                return "Error: Target host could not be resolved: " + hostOf(url);
            }
        }
        log.warn("Web scraping failed for URL '{}': {}", url, e.getMessage());
        return "Error fetching web page (" + url + "): the page could not be retrieved.";
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static String validateTarget(String url) {
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return "Error: Invalid URL scheme. Only HTTP and HTTPS URLs are permitted.";
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "Error: Invalid URL format.";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "Error: URL does not specify a valid host.";
        }
        return null;
    }

    private static CloseableHttpClient buildHttpClient(DnsResolver dnsResolver) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom().setConnectTimeout(TIMEOUT)
                .setSocketTimeout(TIMEOUT).build();
        RequestConfig requestConfig = RequestConfig.custom().setResponseTimeout(TIMEOUT).build();
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(dnsResolver)
                        .setDefaultConnectionConfig(connectionConfig).build())
                .setDefaultRequestConfig(requestConfig).setUserAgent(USER_AGENT).disableRedirectHandling()
                .disableAutomaticRetries().disableCookieManagement().build();
    }

    @PreDestroy
    void close() {
        httpClient.close(CloseMode.GRACEFUL);
    }

    private static String extractText(String url, Document document) {
        document.select(NON_CONTENT_ELEMENTS).remove();
        String text = document.body() != null ? document.body().text() : document.text();
        if (text == null || text.isBlank()) {
            return "The web page at " + url + " contained no readable text content.";
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() > MAX_CONTENT_LENGTH) {
            return text.substring(0, MAX_CONTENT_LENGTH) + "... [Content truncated to 4,000 characters]";
        }
        return text;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    public ToolCallback toolCallback() {
        return FunctionToolCallback
                .builder(NAME, (WebScrapeRequest request) -> readWebPage(request != null ? request.url() : null))
                .description(DESCRIPTION).inputType(WebScrapeRequest.class).build();
    }
}
