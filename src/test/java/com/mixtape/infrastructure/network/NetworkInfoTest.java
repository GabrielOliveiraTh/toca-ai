package com.mixtape.infrastructure.network;

import com.mixtape.infrastructure.network.NetworkInfo.Address;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkInfoTest {
    @Test
    void addressesNeverThrowAndOnlyReturnPrivateIpv4() {
        List<Address> list = assertDoesNotThrow(NetworkInfo::addresses);
        for (Address a : list) {
            assertTrue(a.ip().matches("\\d+\\.\\d+\\.\\d+\\.\\d+"), a.ip());
            assertFalse(a.ip().startsWith("127."));
        }
    }

    @Test
    void sortsByPrivateRangeAndDemotesVirtualAdapters() {
        List<Address> sorted = NetworkInfo.sort(List.of(
                new Address("10.0.0.5", "Ethernet"),
                new Address("192.168.56.1", "VirtualBox Host-Only Network"),
                new Address("172.20.1.2", "Wi-Fi"),
                new Address("192.168.1.10", "Ethernet"),
                new Address("172.40.0.1", "Ethernet"),
                new Address("172.29.80.1", "vEthernet (WSL)"),
                new Address("192.168.0.10", "Wi-Fi")));
        assertEquals(List.of("192.168.1.10", "192.168.0.10", "10.0.0.5", "172.20.1.2", "172.40.0.1", "192.168.56.1", "172.29.80.1"),
                sorted.stream().map(Address::ip).toList());
    }

    @Test
    void recognizesVirtualAdapterNames() {
        assertTrue(NetworkInfo.isVirtual("vEthernet (Default Switch)"));
        assertTrue(NetworkInfo.isVirtual("VMware Virtual Ethernet Adapter for VMnet8"));
        assertTrue(NetworkInfo.isVirtual("Hyper-V Virtual Ethernet Adapter"));
        assertTrue(NetworkInfo.isVirtual("Docker Desktop"));
        assertTrue(NetworkInfo.isVirtual("Bluetooth Network Connection"));
        assertFalse(NetworkInfo.isVirtual("Intel(R) Ethernet Connection"));
        assertFalse(NetworkInfo.isVirtual("Wi-Fi"));
        assertFalse(NetworkInfo.isVirtual(null));
    }

    @Test
    void rankKeepsTheDocumentedOrder() {
        assertTrue(NetworkInfo.rank(new Address("192.168.1.2", "Ethernet")) < NetworkInfo.rank(new Address("10.1.1.1", "Ethernet")));
        assertTrue(NetworkInfo.rank(new Address("10.1.1.1", "Ethernet")) < NetworkInfo.rank(new Address("172.16.0.1", "Ethernet")));
        assertTrue(NetworkInfo.rank(new Address("172.31.0.1", "Ethernet")) < NetworkInfo.rank(new Address("172.32.0.1", "Ethernet")));
        assertTrue(NetworkInfo.rank(new Address("8.8.8.8", "Ethernet")) < NetworkInfo.rank(new Address("192.168.1.2", "vEthernet")));
    }
}
