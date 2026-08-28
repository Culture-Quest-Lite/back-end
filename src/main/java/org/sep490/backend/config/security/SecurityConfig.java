package org.sep490.backend.config.security;

import org.sep490.backend.module.authorization.constant.PermissionCode;
import org.sep490.backend.module.authorization.service.PermissionCacheService;
import org.sep490.backend.module.authorization.util.PermissionResolver;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.service.UserIdCacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.stream.Collectors;

@EnableWebSecurity
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    private static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs",
            "/api/test-ws/**"
    };

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/refresh-token",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/reset-password/open",
            "/api/auth/verify-otp",
            "/api/auth/resend-otp",
            "/api/auth/login-by-google",
            "/api/auth/login-by-facebook"
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/hotspots/**",
            "/api/v1/stories/**",
            "/api/v1/routes/**",
            "/api/v2/routes/**",
            "/api/tags/**",
            "/api/posts/**",
            "/api/users/leaderboard",
            "/api/vouchers/**",
            "/api/v1/reviews/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDenylistFilter jwtDenylistFilter,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/partner/subscriptions/{id}/initiate-payment").permitAll()
                        .requestMatchers("/api/payment/payos/webhook").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // Ngoại lệ PHẢI đứng trước rule /api/admin/** bên dưới
                        .requestMatchers(HttpMethod.GET, "/api/admin/subscription-plans").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/curator/**").hasAnyRole("CURATOR", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Đặt SAU BearerTokenAuthenticationFilter để SecurityContext đã có Jwt
                .addFilterAfter(jwtDenylistFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            PermissionCacheService permissionCacheService,
            UserIdCacheService userIdCacheService) {

        JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        defaultAuthoritiesConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = defaultAuthoritiesConverter.convert(jwt);

            // --- 1. Role từ Keycloak + quyền của role ---
            Set<String> rolePermissions = new HashSet<>();
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                for (Object raw : roles) {
                    String roleName = raw.toString().toUpperCase();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));

                    UserRole userRole = PermissionResolver.parseRole(roleName);
                    if (userRole != null) {
                        // Gọi qua bean -> đi qua proxy -> cache hoạt động
                        rolePermissions.addAll(permissionCacheService.getByRole(userRole));
                    }
                }
            }

            // --- 2. Ngoại lệ cá nhân, đè lên quyền của role ---
            Set<String> finalPermissions = rolePermissions;
            Long userId = userIdCacheService.resolveUserId(jwt.getSubject());   // đã cache 30'
            if (userId != null) {
                finalPermissions = PermissionResolver.merge(
                        rolePermissions, permissionCacheService.getUserOverrides(userId));
            }

            // --- 3. Đổ vào authorities ---
            finalPermissions.forEach(code ->
                    authorities.add(new SimpleGrantedAuthority(PermissionCode.PREFIX + code)));

            return authorities;
        });
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
