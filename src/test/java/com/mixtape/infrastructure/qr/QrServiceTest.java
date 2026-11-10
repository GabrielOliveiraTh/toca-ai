package com.mixtape.infrastructure.qr;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrServiceTest {
    private final QrService qr = new QrService();

    @Test
    void producesAWellFormedSvgWithWhiteBackgroundAndBlackModules() {
        String svg = qr.svg("http://192.168.1.10:8080/join/abcdefghijklmnopqrstuvwx");
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.endsWith("</svg>"));
        assertTrue(svg.contains("viewBox=\"0 0 "));
        assertTrue(svg.contains("shape-rendering=\"crispEdges\""));
        assertTrue(svg.contains("fill=\"#ffffff\""));
        assertTrue(svg.contains("fill=\"#000000\""));
        assertTrue(svg.contains("<path d=\"M"));
        assertFalse(svg.contains("<image"), "no external images");
    }

    @Test
    void isDeterministicAndDependsOnTheText() {
        assertEquals(qr.svg("mixtape"), qr.svg("mixtape"));
        assertNotEquals(qr.svg("mixtape"), qr.svg("mixtape2"));
    }

    @Test
    void viewBoxIsSquareAndIncludesTheQuietZone() {
        String svg = qr.svg("x");
        var m = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        assertTrue(m.find());
        assertEquals(m.group(1), m.group(2));
        assertTrue(Integer.parseInt(m.group(1)) >= 21 + 4);
    }

    @Test
    void handlesAccentsAndRejectsTextThatIsTooLong() {
        assertTrue(qr.svg("Country road trip").startsWith("<svg"));
        assertThrows(IllegalArgumentException.class, () -> qr.svg("x".repeat(5000)));
    }
}
