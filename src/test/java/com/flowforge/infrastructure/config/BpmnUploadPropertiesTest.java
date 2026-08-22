package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BpmnUploadPropertiesTest {

    @Test
    void isAllowed_contentTypeInAllowlist_returnsTrue() {
        BpmnUploadProperties properties = new BpmnUploadProperties(List.of("application/xml"));

        assertThat(properties.isAllowed("application/xml")).isTrue();
    }

    @Test
    void isAllowed_contentTypeNotInAllowlist_returnsFalse() {
        BpmnUploadProperties properties = new BpmnUploadProperties(List.of("application/xml"));

        assertThat(properties.isAllowed("application/pdf")).isFalse();
    }

    @Test
    void defaults_appliedWhenNoConfigProvided() {
        BpmnUploadProperties properties = new BpmnUploadProperties(null);

        assertThat(properties.isAllowed("application/xml")).isTrue();
        assertThat(properties.isAllowed("text/xml")).isTrue();
    }
}
