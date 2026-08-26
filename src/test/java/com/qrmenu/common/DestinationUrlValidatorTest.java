package com.qrmenu.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinationUrlValidatorTest {

    private final DestinationUrlValidator validator = new DestinationUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/menu.pdf",
            "http://example.com/menu",
            "https://sub.domain.example.com/path?x=1"
    })
    void acceptsHttpAndHttpsUrls(String url) {
        assertThatCode(() -> validator.validate(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html;base64,PHNjcmlwdD4=",
            "file:///etc/passwd",
            "ftp://example.com/menu.pdf",
            "not-a-url",
            ""
    })
    void rejectsDangerousOrInvalidUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsNullUrl() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidUrlException.class);
    }
}
