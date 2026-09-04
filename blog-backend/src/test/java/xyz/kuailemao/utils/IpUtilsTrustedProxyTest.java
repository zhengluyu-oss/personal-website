package xyz.kuailemao.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpUtilsTrustedProxyTest {
    @Test
    void directClientCannotSpoofForwardingHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Real-IP", "198.51.100.7");
        request.addHeader("X-Forwarded-For", "198.51.100.8");
        assertEquals("203.0.113.10", IpUtils.getIpAddr(request));
    }

    @Test
    void localReverseProxyCanSupplyCanonicalSingleAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.11");
        assertEquals("203.0.113.11", IpUtils.getIpAddr(request));
    }

    @Test
    void proxyCannotPassAnUntrustedAddressChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.11, 198.51.100.9");
        assertEquals("127.0.0.1", IpUtils.getIpAddr(request));
    }
}
