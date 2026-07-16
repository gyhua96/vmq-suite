package com.vone.mq.utils;

import java.net.InetAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

public final class UrlSecurityUtil {
    private UrlSecurityUtil() {}

    public static boolean isSafePublicCallbackUrl(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) return false;
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme)) return false;
            if (uri.getUserInfo() != null || uri.getFragment() != null) return false;
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) return false;
            String lower = host.toLowerCase();
            if ("localhost".equals(lower) || lower.endsWith(".localhost")) return false;
            return arePublic(resolvePublic(host));
        } catch (Exception e) {
            return false;
        }
    }

    public static Set<String> resolvePublic(String host) throws Exception {
        Set<String> addresses = new HashSet<>();
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isPrivateOrLocal(address)) {
                throw new IllegalArgumentException("callback host resolves to a private address");
            }
            addresses.add(address.getHostAddress());
        }
        return addresses;
    }

    public static boolean samePublicResolution(String host, Set<String> expected) {
        try {
            return expected.equals(resolvePublic(host));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean arePublic(Set<String> addresses) {
        return !addresses.isEmpty();
    }

    private static boolean isPrivateOrLocal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            if (first == 10) return true;
            if (first == 172 && second >= 16 && second <= 31) return true;
            if (first == 192 && second == 168) return true;
            if (first == 127) return true;
            if (first == 169 && second == 254) return true;
            if (first == 0) return true;
        }
        return false;
    }
}
