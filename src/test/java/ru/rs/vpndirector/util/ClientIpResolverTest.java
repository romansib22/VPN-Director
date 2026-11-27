package ru.rs.vpndirector.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.rs.vpndirector.config.BruteForceProperties;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private final BruteForceProperties properties = new BruteForceProperties(); // доверенные: 127.0.0.1, ::1
    private final ClientIpResolver resolver = new ClientIpResolver(properties);

    @Test
    void headersFromUntrustedRemoteAreIgnored() {
        assertEquals("203.0.113.5", resolver.resolve(request("203.0.113.5", "6.6.6.6", "6.6.6.6, 4.4.4.4")));
    }

    @Test
    void realIpFromTrustedProxyWins() {
        assertEquals("203.0.113.7", resolver.resolve(request("127.0.0.1", "203.0.113.7", "6.6.6.6, 4.4.4.4")));
    }

    @Test
    void lastForwardedElementIsUsedBecauseNginxAppendsClientAddress() {
        assertEquals("4.4.4.4", resolver.resolve(request("127.0.0.1", null, "6.6.6.6, 7.7.7.7, 4.4.4.4")));
    }

    @Test
    void invalidHeaderValuesAreSkipped() {
        assertEquals("4.4.4.4", resolver.resolve(request("127.0.0.1", "unknown", "unknown, 4.4.4.4")));
        // "abc.def" похоже на имя из hex-символов — не IP и не должно резолвиться через DNS
        assertEquals("127.0.0.1", resolver.resolve(request("127.0.0.1", "abc.def", "garbage")));
        assertEquals("127.0.0.1", resolver.resolve(request("127.0.0.1", "999.1.1.1", null)));
    }

    @Test
    void fallsBackToRemoteAddressWithoutHeaders() {
        assertEquals("127.0.0.1", resolver.resolve(request("127.0.0.1", null, null)));
    }

    @Test
    void ipv6LoopbackIsTrustedProxy() {
        assertEquals("2001:db8:0:0:0:0:0:7", resolver.resolve(request("0:0:0:0:0:0:0:1", "2001:db8::7", null)));
    }

    @Test
    void trustedProxiesAreConfigurable() {
        properties.setTrustedProxies(new ArrayList<>(List.of("10.0.0.1")));
        assertEquals("203.0.113.9", resolver.resolve(request("10.0.0.1", "203.0.113.9", null)));
        assertEquals("127.0.0.1", resolver.resolve(request("127.0.0.1", "203.0.113.9", null)));
    }

    private static MockHttpServletRequest request(String remoteAddr, String realIp, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (realIp != null) {
            request.addHeader("X-Real-IP", realIp);
        }
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
