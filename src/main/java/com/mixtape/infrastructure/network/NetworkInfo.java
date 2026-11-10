package com.mixtape.infrastructure.network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class NetworkInfo {
    private static final List<String> VIRTUAL = List.of(
            "vethernet", "virtualbox", "vmware", "hyper-v", "docker", "wsl", "loopback", "bluetooth", "tailscale",
            "vpn");
    private static final int VIRTUAL_PENALTY = 10;

    private NetworkInfo() {
    }

    public record Address(String ip, String name) {
    }

    public static List<Address> addresses() {
        List<Address> out = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                String name = network.getDisplayName() != null ? network.getDisplayName() : network.getName();
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        out.add(new Address(address.getHostAddress(), name));
                    }
                }
            }
        } catch (SocketException e) {
            return sort(out);
        }
        return sort(out);
    }

    static boolean isVirtual(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return VIRTUAL.stream().anyMatch(lower::contains);
    }

    static int rank(Address address) {
        int base;
        if (address.ip().startsWith("192.168.")) {
            base = 0;
        } else if (address.ip().startsWith("10.")) {
            base = 1;
        } else if (isRfc1918Class172(address.ip())) {
            base = 2;
        } else {
            base = 3;
        }
        return isVirtual(address.name()) ? base + VIRTUAL_PENALTY : base;
    }

    private static boolean isRfc1918Class172(String ip) {
        if (!ip.startsWith("172.")) {
            return false;
        }
        try {
            int second = Integer.parseInt(ip.split("\\.")[1]);
            return second >= 16 && second <= 31;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static List<Address> sort(List<Address> addresses) {
        List<Address> sorted = new ArrayList<>(addresses);
        sorted.sort(Comparator.comparingInt(NetworkInfo::rank));
        return sorted;
    }
}
