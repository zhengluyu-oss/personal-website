package xyz.kuailemao.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.dto.OauthExchangeDTO;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.handler.SecurityHandler;
import xyz.kuailemao.service.OauthService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OauthControllerExchangeTest {
    private OauthController controller;
    private OauthService oauthService;
    private SecurityHandler securityHandler;

    @BeforeEach
    void setUp() {
        controller = new OauthController();
        oauthService = mock(OauthService.class);
        securityHandler = mock(SecurityHandler.class);
        ReflectionTestUtils.setField(controller, "oauthService", oauthService);
        ReflectionTestUtils.setField(controller, "securityHandler", securityHandler);
    }

    @Test
    void validHandoffUsesNormalLoginHandlerSoAdminSecondFactorIsPreserved() {
        OauthExchangeDTO dto = new OauthExchangeDTO();
        dto.setCode("a".repeat(64));
        LoginUser loginUser = new LoginUser();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/exchange");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(oauthService.exchangeCode(dto.getCode())).thenReturn(loginUser);

        controller.exchange(dto, request, response);

        verify(securityHandler).handlerOnAuthenticationSuccess(request, response, loginUser);
        verify(securityHandler, never()).issueToken(any(), any(), any());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void expiredHandoffReturnsControlledErrorWithoutIssuingToken() throws Exception {
        OauthExchangeDTO dto = new OauthExchangeDTO();
        dto.setCode("b".repeat(64));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/exchange");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(oauthService.exchangeCode(dto.getCode())).thenThrow(new BadCredentialsException("internal detail"));

        controller.exchange(dto, request, response);

        assertTrue(response.getContentAsString().contains("\"code\":1001"));
        assertFalse(response.getContentAsString().contains("internal detail"));
        verify(securityHandler, never()).handlerOnAuthenticationSuccess(any(), any(), any());
    }
}
