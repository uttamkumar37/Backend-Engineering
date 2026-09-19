package com.backendplan.security.beginner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Properties;

// The filter chain, not your controller code, decides who reaches an endpoint at all -
// by the time a controller method runs, authentication/authorization has already happened.
public class SpringSecurityFilterChainDemo {

    @RestController
    static class DemoController {
        @GetMapping("/public/health")
        String health() { return "OK - no authentication needed"; }

        @GetMapping("/private/profile")
        String profile() { return "secret profile data"; }
    }

    @SpringBootApplication
    static class App {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/public/**").permitAll()
                            .anyRequest().authenticated())
                    .httpBasic(basic -> {})
                    .csrf(csrf -> csrf.disable()); // stateless demo, no cookie-based session risk here
            return http.build();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            // NoOp only for this demo's readability - real code always uses BCryptPasswordEncoder
            return NoOpPasswordEncoder.getInstance();
        }

        @Bean
        InMemoryUserDetailsManager userDetailsManager(PasswordEncoder encoder) {
            return new InMemoryUserDetailsManager(
                    org.springframework.security.core.userdetails.User
                            .withUsername("uttam")
                            .password(encoder.encode("password123"))
                            .roles("USER")
                            .build());
        }
    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("server.port", "8085");

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(App.class)
                .properties(props)
                .run(args)) {

            RestTemplate rest = new RestTemplate();

            String publicResponse = rest.getForObject("http://localhost:8085/public/health", String.class);
            System.out.println("GET /public/health (no credentials): " + publicResponse);

            try {
                rest.getForObject("http://localhost:8085/private/profile", String.class);
            } catch (HttpClientErrorException e) {
                System.out.println("GET /private/profile (no credentials): " + e.getStatusCode() + " - rejected before the controller ever ran");
            }

            RestTemplate authedRest = new RestTemplate();
            authedRest.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add("Authorization",
                        "Basic " + java.util.Base64.getEncoder().encodeToString("uttam:password123".getBytes()));
                return execution.execute(request, body);
            });
            String privateResponse = authedRest.getForObject("http://localhost:8085/private/profile", String.class);
            System.out.println("GET /private/profile (with credentials): " + privateResponse);
        }
    }
}
