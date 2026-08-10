package ru.rs.vpndirector.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.rs.vpndirector.service.LoginAttemptService;
import ru.rs.vpndirector.util.ClientIpResolver;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Блокирует POST /login для IP, превысивших лимит неудачных попыток.
 */
@Component
@RequiredArgsConstructor
public class LoginIpBlockFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;
    private final LoginFailureHandler loginFailureHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isLoginPost(request)) {
            String ip = ClientIpResolver.resolve(request);
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
