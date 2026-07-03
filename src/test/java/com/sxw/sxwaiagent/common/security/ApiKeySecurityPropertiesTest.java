package com.sxw.sxwaiagent.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeySecurityPropertiesTest {

    @Test
    void disabledByDefaultEvenWhenValueIsBlank() {
        ApiKeySecurityProperties properties = new ApiKeySecurityProperties();

        assertFalse(properties.isEffective());
    }

    @Test
    void enabledWithBlankValueIsNotEffective() {
        ApiKeySecurityProperties properties = new ApiKeySecurityProperties();
        properties.setEnabled(true);
        properties.setValue("  ");

        assertFalse(properties.isEffective());
    }

    @Test
    void enabledWithValueIsEffective() {
        ApiKeySecurityProperties properties = new ApiKeySecurityProperties();
        properties.setEnabled(true);
        properties.setValue("secret");

        assertTrue(properties.isEffective());
    }
}
