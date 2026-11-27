package ru.rs.vpndirector.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import ru.rs.vpndirector.service.LoginAttemptService;
import ru.rs.vpndirector.util.ClientIpResolver;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private static final String[] STATIC_RESOURCES = {
        "/css/**", "/js/**", "/webjars/**", "/favicon.png", "/favicon.ico", "/images/**"
    };

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private final SecurityProperties securityProperties;
    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // permitAll() у formLogin разрешает лишь точные URL (query-string включается в сравнение),
                // а /login?error регистрирует только для стандартного failureHandler. С кастомными
                // handler'ами /login?error, /login?blocked, /login?expired иначе закрыты, и анонимный
                // запрос перенаправляется на /login без сообщения (подтверждено TRACE-логом фильтров)
                .requestMatchers("/login").permitAll()
                // Ошибка на открытой странице не должна превращаться в редирект на /login
                .requestMatchers("/error").permitAll()
                .requestMatchers(STATIC_RESOURCES).permitAll()
                .anyRequest().authenticated())
            // Заблокированные по IP POST /login отсекаются до проверки пароля
            .addFilterBefore(new LoginIpBlockFilter(loginAttemptService, loginFailureHandler, clientIpResolver),
                UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form
                .loginPage("/login")
                // Счётчики перебора: сброс при успехе, +1 и блокировка IP при неудаче
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll())
            // Отказ CSRF-фильтра обрабатывает accessDeniedHandler из exceptionHandling
            .exceptionHandling(e -> e.accessDeniedHandler(csrfAccessDeniedHandler()))
            // CSRF включён (по умолчанию): все POST-формы используют th:action, токен подставляется автоматически
            .csrf(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer(RequestRejectedHandler requestRejectedHandler) {
        // Явно: отклонение firewall не пробрасывается как необработанное исключение
        return web -> web.requestRejectedHandler(requestRejectedHandler);
    }

    /**
     * Подозрительный URL (например, с ";") → HTTP 400 и WARN, без падения сервиса и без ERROR-стека.
     */
    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return (request, response, ex) -> {
            log.warn("Отклонён подозрительный запрос: {} {} — {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
            if (!response.isCommitted()) {
                new HttpStatusRequestRejectedHandler().handle(request, response, ex);
            }
        };
    }

    /**
     * POST без действующего CSRF-токена: если сессия истекла — на страницу входа с пояснением,
     * иначе (токен отсутствует/не совпал у авторизованного) — 403.
     */
    @Bean
    public AccessDeniedHandler csrfAccessDeniedHandler() {
        return (request, response, ex) -> {
            if (response.isCommitted()) {
                return;
            }
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean anonymous = authentication == null || TRUST_RESOLVER.isAnonymous(authentication);
            if (anonymous) {
                new DefaultRedirectStrategy().sendRedirect(request, response, "/login?expired");
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        };
    }

    @Bean
    public UserDetailsService userDetailsService() {
        String password = securityProperties.getPassword();
        // Без пароля не стартуем: пустой пароль иначе проходит проверку BCrypt (пароль не задан — вход открыт)
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "Не задан пароль веб-интерфейса. Укажите SECURITY_USER_PASSWORD в .env рядом с jar "
                + "(образец — .env_example) или в переменной окружения и перезапустите приложение.");
        }

        UserDetails user = User.builder()
            .username(securityProperties.getName())
            .password(passwordEncoder().encode(password))
            .roles("USER", "ADMIN")
            .build();

        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
