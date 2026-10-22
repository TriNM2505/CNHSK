package com.cnhsk.shared.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class Masking {

    private static final String MASK = "•••";

    private Masking() {
    }

    public static String email(String email) {
        if (email == null) {
            return null;
        }

        int separator = email.indexOf('@');
        if (separator <= 0 || separator != email.lastIndexOf('@') || separator == email.length() - 1) {
            return MASK;
        }

        String localPart = email.substring(0, separator);
        int visibleCodePoints = Math.min(2, localPart.codePointCount(0, localPart.length()));
        int visibleEnd = localPart.offsetByCodePoints(0, visibleCodePoints);
        return localPart.substring(0, visibleEnd) + MASK + email.substring(separator);
    }

    public static String ip(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }

        String address = ipAddress.strip();
        if (isIpv4(address)) {
            String[] octets = address.split("\\.");
            return octets[0] + "." + octets[1] + ".x.x";
        }

        if (address.indexOf(':') >= 0) {
            return maskIpv6(address);
        }
        return "x.x.x.x";
    }

    private static boolean isIpv4(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }

        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit)) {
                return false;
            }
            try {
                if (Integer.parseInt(octet) > 255) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    private static String maskIpv6(String address) {
        try {
            InetAddress parsed = InetAddress.getByName(address);
            if (!(parsed instanceof Inet6Address)) {
                return "x.x.x.x";
            }

            byte[] bytes = parsed.getAddress();
            StringBuilder masked = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (i > 0) {
                    masked.append(':');
                }
                int group = ((bytes[i * 2] & 0xff) << 8) | (bytes[i * 2 + 1] & 0xff);
                masked.append(Integer.toHexString(group));
            }
            return masked.append(":x:x:x:x").toString();
        } catch (UnknownHostException exception) {
            return "x.x.x.x";
        }
    }
}
