package ru.rs.vpndirector.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final SecurityProperties securityProperties;

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/css/**", "/js/**", "/webjars/**", "/favicon.png", "/favicon.ico", "/images/**");
        // Явно: отклонение firewall не пробрасывается как необработанное исключение
        web.requestRejectedHandler(requestRejectedHandler());
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

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/css/**", "/js/**", "/webjars/**", "/favicon.png", "/favicon.ico", "/images/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            .and()
            .csrf().disable(); // Отключаем CSRF для упрощения (можно включить позже)
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
            .username(securityProperties.getName())
            .password(passwordEncoder().encode(securityProperties.getPassword()))
            .roles("USER", "ADMIN")
            .build();

        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

