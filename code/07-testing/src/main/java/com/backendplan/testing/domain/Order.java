package com.backendplan.testing.domain;

public record Order(String id, Money total, String status) {}
