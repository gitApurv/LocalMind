package com.localmind.tools.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link RestrictedAddresses}: private, CGNAT, unspecified, multicast and special-purpose IPv4 ranges,
 * IPv6 unique-local, and IPv6 forms that embed a restricted IPv4 address.
 */
class RestrictedAddressesTest {

    @Test
    @DisplayName("isRestricted() should cover CGNAT, IPv6 unique-local, unspecified and multicast ranges")
    void restrictsAdditionalPrivateRanges() throws Exception {
        assertTrue(RestrictedAddresses.isRestricted(InetAddress.getByName("100.64.0.1")));
        assertTrue(RestrictedAddresses.isRestricted(InetAddress.getByName("fd12:3456::1")));
        assertTrue(RestrictedAddresses.isRestricted(InetAddress.getByName("0.0.0.0")));
        assertTrue(RestrictedAddresses.isRestricted(InetAddress.getByName("224.0.0.1")));
        assertTrue(RestrictedAddresses.isRestricted(InetAddress.getByName("::ffff:10.0.0.1")));
        assertFalse(RestrictedAddresses.isRestricted(InetAddress.getByName("93.184.216.34")));
    }

    @ParameterizedTest(name = "{0} restricted = {1}")
    @CsvSource({"192.0.0.8, true", "198.18.0.1, true", "198.19.255.255, true",
            "240.0.0.1, true", "255.255.255.255, true", "64:ff9b::a00:1, true", "64:ff9b::808:808, false",
            "64:ff9b:1::1, true", "2002:a00:1::1, true", "2002:808:808::1, false", "2001:0:4136:e378::1, true",
            "::a00:1, true", "::ffff:808:808, false", "198.20.0.1, false", "192.0.2.1, false",
            "2606:4700::1111, false"})
    @DisplayName("isRestricted() should cover special-purpose IPv4 ranges and IPv6 forms that embed IPv4")
    void restrictsSpecialPurposeAndEmbeddedRanges(String address, boolean restricted) throws Exception {
        assertEquals(restricted, RestrictedAddresses.isRestricted(InetAddress.getByName(address)));
    }
}
