package ru.rs.vpndirector.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.rs.vpndirector.service.LoginAttemptService;
import ru.rs.vpndirector.util.ClientIpResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Блокирует POST /login для IP, превысивших лимит неудачных попыток.
 * <p>
 * Не является servlet-фильтром: экземпляр включается в цепочку Spring Security
 * непосредственно перед {@link UsernamePasswordAuthenticationFilter} (см. {@link SecurityConfig}),
 * иначе проверка пароля выполнится раньше блокировки.
 */
@RequiredArgsConstructor
public class LoginIpBlockFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;
    private final LoginFailureHandler loginFailureHandler;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isLoginPost(request)) {
            String ip = clientIpResolver.resolve(request);
            if (loginAttemptService.isBlocked(ip)) {
                loginFailureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new LockedException("IP временно заблокирован из-за слишком большого числа неудачных попыток входа")
                );
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLoginPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
            && request.getServletPath() != null
            && "/login".equals(request.getServletPath());
    }
}
