package com.localmind.tools.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

/**
 * Resolves hosts for the {@link WebScraperTools} HTTP client and refuses any host with a restricted address. The client
 * connects only to the addresses returned here, so the check and the connection always use the same DNS answer (no DNS
 * rebinding).
 */
@Slf4j
@RequiredArgsConstructor
final class GuardedDnsResolver implements DnsResolver {

    private final boolean allowPrivateSubnets;
    private final Predicate<InetAddress> restrictedAddress;
    private final HostLookup hostLookup;

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        if (allowPrivateSubnets) {
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

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
    }

    private static BlockedHostException blocked(String host, InetAddress address) {
        log.warn("SSRF blocked attempt to connect to restricted host '{}' resolved to '{}'", host,
                address.getHostAddress());
        return new BlockedHostException(host);
    }
}
