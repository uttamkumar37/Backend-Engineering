package com.backendplan.security.intermediate;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

// @PreAuthorize is AOP, exactly like @Transactional - it's enforced by a proxy wrapping the
// bean. Calling a @PreAuthorize method via `this` from inside the same class bypasses the
// proxy entirely, silently skipping the authorization check. This is a genuine, dangerous bug
// class, not a theoretical one.
public class MethodSecuritySelfInvocationDemo {

    static class ReportService {
        // only ADMIN should ever reach this
        @PreAuthorize("hasRole('ADMIN')")
        String deleteAllReports() {
            return "ALL REPORTS DELETED";
        }

        // calls the protected method via `this` - the proxy is bypassed
        String adminAction() {
            return this.deleteAllReports();
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        ReportService reportService() {
            return new ReportService();
        }
    }

    public static void main(String[] args) {
        // authenticate as a plain USER - explicitly NOT an admin
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("regular-user", "n/a", "ROLE_USER"));

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class)) {
            ReportService service = ctx.getBean(ReportService.class);

            System.out.println("--- Calling the protected method DIRECTLY through the proxy ---");
            try {
                service.deleteAllReports();
                System.out.println("This should not print - a non-admin should be denied.");
            } catch (AccessDeniedException e) {
                System.out.println("Correctly DENIED: " + e.getMessage());
            }

            System.out.println();
            System.out.println("--- Calling the SAME protected method via self-invocation (this.deleteAllReports()) ---");
            String result = service.adminAction();
            System.out.println("Result: " + result);
            System.out.println("BUG: a non-admin user just ran an admin-only action, because the proxy was bypassed.");
        }

        SecurityContextHolder.clearContext();
    }
}
