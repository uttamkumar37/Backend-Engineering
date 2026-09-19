package com.backendplan.testing.beginner;

import com.backendplan.testing.domain.EmailValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

// One parameterized test replaces what would otherwise be 6 near-identical @Test methods -
// the whole matrix of cases is visible in the annotation, not buried in method names.
class ParameterizedEmailValidatorTest {

    private final EmailValidator validator = new EmailValidator();

    @ParameterizedTest
    @CsvSource({
            "uttam@example.com, true",
            "uttam.kumar@example.co.in, true",
            "not-an-email, false",
            "'', false",
            "missing-domain@, false",
            "@missing-local.com, false"
    })
    void validatesEmailFormats(String email, boolean expected) {
        assertEquals(expected, validator.isValid(email.isEmpty() ? null : email));
    }
}
