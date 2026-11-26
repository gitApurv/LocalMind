package com.localmind.tools.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves a host name to all its addresses for {@link GuardedDnsResolver}; production uses
 * {@link InetAddress#getAllByName}, tests substitute fixed answers to simulate DNS rebinding.
 */
@FunctionalInterface
interface HostLookup {

    InetAddress[] lookup(String host) throws UnknownHostException;
}
