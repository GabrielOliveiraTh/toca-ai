package com.mixtape.infrastructure.network;

import com.mixtape.application.port.ShareAddresses;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalShareAddressesTest {
    private static final List<NetworkInfo.Address> DETECTED = List.of(
            new NetworkInfo.Address("192.168.0.10", "Wi-Fi"),
            new NetworkInfo.Address("10.0.0.5", "Ethernet"));

    private static LocalShareAddresses with(String host) {
        return new LocalShareAddresses(new ShareProperties(host), () -> DETECTED);
    }

    @Test
    void configuredHostComesFirstThenTheDetectedOnes() {
        List<ShareAddresses.Address> addresses = with("192.168.1.10").addresses();
        assertEquals(List.of("192.168.1.10", "192.168.0.10", "10.0.0.5"),
                addresses.stream().map(ShareAddresses.Address::ip).toList());
        assertEquals("Configured", addresses.get(0).name());
        assertEquals("192.168.1.10", with("192.168.1.10").configuredHost());
    }

    @Test
    void aDetectedAddressEqualToTheConfiguredOneIsNotRepeated() {
        List<ShareAddresses.Address> addresses = with("192.168.0.10").addresses();
        assertEquals(List.of("192.168.0.10", "10.0.0.5"), addresses.stream().map(ShareAddresses.Address::ip).toList());
        assertEquals("Configured", addresses.get(0).name());
    }

    @Test
    void withoutAConfiguredHostTheDetectedOrderIsKept() {
        assertEquals(List.of("192.168.0.10", "10.0.0.5"),
                with("").addresses().stream().map(ShareAddresses.Address::ip).toList());
        assertNull(with(null).configuredHost());
    }

    @Test
    void invalidHostsAreRejectedWithAClearMessage() {
        for (String invalid : List.of("http://192.168.1.10", "192.168.1.10/path", "my host", "192.168.1.10:8080")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new ShareProperties(invalid));
            assertEquals(true, error.getMessage().contains("share.host"));
        }
    }

    @Test
    void ipAddressesAndHostNamesAreAccepted() {
        assertEquals("192.168.1.10", new ShareProperties(" 192.168.1.10 ").host());
        assertEquals("my-laptop.local", new ShareProperties("my-laptop.local").host());
    }
}
