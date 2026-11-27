package ru.rs.vpndirector.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import ru.rs.vpndirector.service.LoginAttemptService;
import ru.rs.vpndirector.util.ClientIpResolver;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;

    public LoginFailureHandler(LoginAttemptService loginAttemptService, ClientIpResolver clientIpResolver) {
        this.loginAttemptService = loginAttemptService;
        this.clientIpResolver = clientIpResolver;
        setDefaultFailureUrl("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String ip = clientIpResolver.resolve(request);
        if (exception instanceof LockedException) {
            getRedirectStrategy().sendRedirect(request, response, "/login?blocked");
            return;
        }
        boolean blocked = loginAttemptService.loginFailed(ip);
        log.warn("Неудачная попытка входа с IP {}: {}", ip, exception.getMessage());
        if (blocked) {
            getRedirectStrategy().sendRedirect(request, response, "/login?blocked");
        } else {
            super.onAuthenticationFailure(request, response, exception);
        }
    }
}
