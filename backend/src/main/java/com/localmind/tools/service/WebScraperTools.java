package com.localmind.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.localmind.tools.config.WebScraperProperties;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fetches a web page and returns its readable text. Requests to loopback and private networks are blocked (SSRF guard)
 * unless explicitly allowed. Redirects are followed manually so that every hop is validated, not just the first URL.
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
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final String USER_AGENT = "LocalMindBot/1.0 (Privacy-first AI Assistant; +https://github.com/gitApurv/LocalMind)";
    private static final String NON_CONTENT_ELEMENTS = "script, style, noscript, nav, footer, header, svg, form";
    private static final String SSRF_BLOCKED = "Error: Access to private or loopback IP addresses is restricted (SSRF protection).";

    private final WebScraperProperties properties;
    private final Predicate<InetAddress> restrictedAddress;

    @Autowired
    public WebScraperTools(WebScraperProperties properties) {
        this(properties, WebScraperTools::isRestrictedAddress);
    }

    /** Lets tests substitute the address rule, e.g. to reach a loopback test server but still block 10/8. */
    WebScraperTools(WebScraperProperties properties, Predicate<InetAddress> restrictedAddress) {
        this.properties = properties;
        this.restrictedAddress = restrictedAddress;
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
                Connection.Response response = Jsoup.connect(currentUrl).userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MILLIS).maxBodySize(MAX_BODY_BYTES).followRedirects(false)
                        .ignoreHttpErrors(true).execute();
                if (isRedirect(response.statusCode()) && response.hasHeader("Location")) {
                    currentUrl = URI.create(currentUrl).resolve(response.header("Location")).toString();
                    continue;
                }
                if (response.statusCode() >= 400) {
                    return "Error fetching web page (" + currentUrl + "): HTTP " + response.statusCode();
                }
                return extractText(currentUrl, response.parse());
            }
            return "Error: Too many redirects (more than " + MAX_REDIRECTS + ").";
        } catch (Exception e) {
            log.warn("Web scraping failed for URL '{}': {}", currentUrl, e.getMessage());
            return "Error fetching web page (" + currentUrl + "): the page could not be retrieved.";
        }
    }

    /** Returns an error message when the URL may not be fetched, or {@code null} when it is allowed. */
    private String validateTarget(String url) {
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
        return properties.allowPrivateSubnets() ? null : checkHostIsPublic(host);
    }

    private String checkHostIsPublic(String host) {
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if ((lowerHost.equals("localhost") || lowerHost.endsWith(".localhost"))
                && restrictedAddress.test(InetAddress.getLoopbackAddress())) {
            return SSRF_BLOCKED;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (restrictedAddress.test(address)) {
                    log.warn("SSRF blocked attempt to connect to restricted host '{}' resolved to '{}'", host,
                            address.getHostAddress());
                    return SSRF_BLOCKED;
                }
            }
            return null;
        } catch (UnknownHostException e) {
            return "Error: Target host could not be resolved: " + host;
        }
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
     * Loopback, private (RFC 1918), link-local, unspecified, multicast, carrier-grade NAT (100.64.0.0/10), IPv6
     * unique-local (fc00::/7) and IPv4-mapped IPv6 forms of all of these.
     */
    static boolean isRestrictedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 0 || (first == 100 && second >= 64 && second <= 127);
        }
        if (address instanceof Inet6Address) {
            if ((bytes[0] & 0xFE) == 0xFC) {
                return true;
            }
            if (isIpv4Mapped(bytes)) {
                try {
                    return isRestrictedAddress(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    public ToolCallback toolCallback() {
        return FunctionToolCallback
                .builder(NAME, (WebScrapeRequest request) -> readWebPage(request != null ? request.url() : null))
                .description(DESCRIPTION).inputType(WebScrapeRequest.class).build();
    }
}
