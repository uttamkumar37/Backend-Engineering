package com.backendplan.testing.domain;

import java.util.regex.Pattern;

public class EmailValidator {
    private static final Pattern PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public boolean isValid(String email) {
        return email != null && PATTERN.matcher(email).matches();
    }
}
