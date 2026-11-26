package com.localmind.tools.service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Decides whether an address is one {@link WebScraperTools} must never connect to: loopback, private, link-local,
 * unspecified, multicast, CGNAT, IPv4 special-purpose ranges, IPv6 unique-local, and IPv6 forms that embed a restricted
 * IPv4 address (NAT64, 6to4, Teredo, IPv4-mapped and IPv4-compatible).
 */
final class RestrictedAddresses {

    private RestrictedAddresses() {
    }

    static boolean isRestricted(InetAddress address) {
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
            return isRestricted(InetAddress.getByAddress(Arrays.copyOfRange(bytes, offset, offset + 4)));
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
}
