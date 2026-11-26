package com.localmind.tools.service;

import java.net.UnknownHostException;

/**
 * Thrown when {@link GuardedDnsResolver} refuses a host that resolves to a restricted address; {@link WebScraperTools}
 * turns it into its fixed SSRF tool message, so it never reaches a client.
 */
final class BlockedHostException extends UnknownHostException {

    BlockedHostException(String host) {
        super("SSRF guard blocked host '" + host + "'");
    }
}
