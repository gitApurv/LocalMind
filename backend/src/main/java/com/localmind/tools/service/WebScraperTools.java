package com.localmind.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.localmind.tools.config.WebScraperProperties;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
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
 * Fetches a web page and returns its readable text. Requests to loopback and private networks are blocked (SSRF guard)
 * unless explicitly allowed.
 * <p>
 * The guard lives in the HTTP client's DNS resolver: the addresses it validates are the only ones the connection may
 * use. Checking a host and then letting the HTTP library resolve it again would let a DNS server answer the check with
 * a public address and the connection with a private one (DNS rebinding). Redirects are followed manually, so every hop
 * goes through the same resolver.
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

    private final WebScraperProperties properties;
    private final Predicate<InetAddress> restrictedAddress;
    private final HostLookup hostLookup;
    private final CloseableHttpClient httpClient;

    @Autowired
    public WebScraperTools(WebScraperProperties properties) {
        this(properties, WebScraperTools::isRestrictedAddress);
    }

    /** Lets tests substitute the address rule, e.g. to reach a loopback test server but still block 10/8. */
    WebScraperTools(WebScraperProperties properties, Predicate<InetAddress> restrictedAddress) {
        this(properties, restrictedAddress, InetAddress::getAllByName);
    }

    /** Lets tests substitute DNS, e.g. to answer the same host differently on successive lookups. */
    WebScraperTools(WebScraperProperties properties, Predicate<InetAddress> restrictedAddress, HostLookup hostLookup) {
        this.properties = properties;
        this.restrictedAddress = restrictedAddress;
        this.hostLookup = hostLookup;
        this.httpClient = buildHttpClient(new GuardedDnsResolver());
    }

    /** Resolves a host name to its addresses; {@link InetAddress#getAllByName} in production. */
    @FunctionalInterface
    interface HostLookup {
        InetAddress[] lookup(String host) throws UnknownHostException;
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

    /** Outcome of one request: either a redirect target or the final text for the model. */
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

    /** Reads at most {@link #MAX_BODY_BYTES}; longer bodies are truncated, like a browser that stops reading. */
    private static Document parse(String url, HttpEntity entity, ContentType contentType) throws IOException {
        byte[] body;
        try (InputStream in = entity.getContent()) {
            body = in.readNBytes(MAX_BODY_BYTES);
        }
        Charset charset = contentType != null ? contentType.getCharset() : null;
        String charsetName = charset != null ? charset.name() : null;
        return Jsoup.parse(new ByteArrayInputStream(body), charsetName, url);
    }

    /** Same rule Jsoup applied before: no type, any {@code text/*}, or an XML type. */
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

    /** Returns an error message when the URL may not be fetched, or {@code null} when it is allowed. */
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

    /**
     * The only source of addresses for the HTTP client. With the guard active it rejects a host when any of its
     * addresses is restricted, so the connection can never reach an address that was not checked.
     */
    private final class GuardedDnsResolver implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            if (properties.allowPrivateSubnets()) {
                return hostLookup.lookup(host);
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            if ((lowerHost.equals("localhost") || lowerHost.endsWith(".localhost"))
                    && restrictedAddress.test(InetAddress.getLoopbackAddress())) {
                throw blocked(host, InetAddress.getLoopbackAddress());
            }
            InetAddress[] addresses = hostLookup.lookup(host);
            for (InetAddress address : addresses) {
                if (restrictedAddress.test(address)) {
                    throw blocked(host, address);
                }
            }
            return addresses;
        }

        private BlockedHostException blocked(String host, InetAddress address) {
            log.warn("SSRF blocked attempt to connect to restricted host '{}' resolved to '{}'", host,
                    address.getHostAddress());
            return new BlockedHostException(host);
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
        }
    }

    /** Raised by the resolver for a restricted address; a subtype of the exception HttpClient expects from DNS. */
    private static final class BlockedHostException extends UnknownHostException {

        BlockedHostException(String host) {
            super("SSRF guard blocked host '" + host + "'");
        }
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

    /**
     * Addresses a model-chosen URL may never reach: loopback, private (RFC 1918), link-local, unspecified, multicast,
     * {@code 0.0.0.0/8}, carrier-grade NAT ({@code 100.64.0.0/10}), IETF protocol assignments ({@code 192.0.0.0/24}),
     * benchmarking ({@code 198.18.0.0/15}), reserved and broadcast ({@code 240.0.0.0/4}), IPv6 unique-local
     * ({@code fc00::/7}) and Teredo ({@code 2001::/32}). IPv6 forms that carry an IPv4 address (IPv4-mapped,
     * IPv4-compatible, NAT64 {@code 64:ff9b::/96}, 6to4 {@code 2002::/16}) are judged by that IPv4 address; the
     * local-use NAT64 prefix {@code 64:ff9b:1::/48} is always blocked.
     */
    static boolean isRestrictedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isRestrictedIpv4(bytes);
        }
        return address instanceof Inet6Address && isRestrictedIpv6(bytes);
    }

    private static boolean isRestrictedIpv4(byte[] bytes) {
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        int third = bytes[2] & 0xFF;
        return first == 0 || (first == 100 && second >= 64 && second <= 127)
                || (first == 192 && second == 0 && third == 0) || (first == 198 && (second == 18 || second == 19))
                || first >= 240;
    }

    private static boolean isRestrictedIpv6(byte[] bytes) {
        int firstWord = word(bytes, 0);
        int secondWord = word(bytes, 2);
        if ((bytes[0] & 0xFE) == 0xFC || (firstWord == 0x2001 && secondWord == 0)) {
            return true;
        }
        if (firstWord == 0x0064 && secondWord == 0xFF9B) {
            return word(bytes, 4) == 0x0001 || embeddedIpv4IsRestricted(bytes, 12);
        }
        if (firstWord == 0x2002) {
            return embeddedIpv4IsRestricted(bytes, 2);
        }
        if (isZero(bytes, 0, 10) && (isIpv4MappedMarker(bytes) || isZero(bytes, 10, 12))) {
            return embeddedIpv4IsRestricted(bytes, 12);
        }
        return false;
    }

    private static boolean embeddedIpv4IsRestricted(byte[] bytes, int offset) {
        try {
            return isRestrictedAddress(InetAddress.getByAddress(Arrays.copyOfRange(bytes, offset, offset + 4)));
        } catch (UnknownHostException e) {
            return true;
        }
    }

    private static int word(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static boolean isZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv4MappedMarker(byte[] bytes) {
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    public ToolCallback toolCallback() {
        return FunctionToolCallback
                .builder(NAME, (WebScrapeRequest request) -> readWebPage(request != null ? request.url() : null))
                .description(DESCRIPTION).inputType(WebScrapeRequest.class).build();
    }
}
