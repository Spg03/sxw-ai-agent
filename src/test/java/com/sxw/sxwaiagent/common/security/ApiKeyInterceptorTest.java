package com.sxw.sxwaiagent.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiKeyInterceptorTest {

    @Test
    void disabledSecurityAllowsRequest() throws Exception {
        ApiKeySecurityProperties properties = new ApiKeySecurityProperties();
        ApiKeyInterceptor interceptor = new ApiKeyInterceptor(properties);

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    void enabledSecurityRejectsMissingKey() throws Exception {
        ApiKeySecurityProperties properties = enabledProperties();
        ApiKeyInterceptor interceptor = new ApiKeyInterceptor(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void enabledSecurityRejectsWrongKey() throws Exception {
        ApiKeySecurityProperties properties = enabledProperties();
        ApiKeyInterceptor interceptor = new ApiKeyInterceptor(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyInterceptor.HEADER_NAME, "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void enabledSecurityAllowsCorrectKey() throws Exception {
        ApiKeySecurityProperties properties = enabledProperties();
        ApiKeyInterceptor interceptor = new ApiKeyInterceptor(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyInterceptor.HEADER_NAME, "secret");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    private static ApiKeySecurityProperties enabledProperties() {
        ApiKeySecurityProperties properties = new ApiKeySecurityProperties();
        properties.setEnabled(true);
        properties.setValue("secret");
        return properties;
    }
}
