package ru.rs.vpndirector.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import ru.rs.vpndirector.service.LoginAttemptService;
import ru.rs.vpndirector.util.ClientIpResolver;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    public LoginFailureHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
        setDefaultFailureUrl("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String ip = ClientIpResolver.resolve(request);
        if (exception instanceof LockedException) {
            getRedirectStrategy().sendRedirect(request, response, "/login?blocked");
            return;
        }
        boolean blocked = loginAttemptService.loginFailed(ip);
        log.info("Неудачная попытка входа с IP {}", ip);
        if (blocked) {
            getRedirectStrategy().sendRedirect(request, response, "/login?blocked");
        } else {
            super.onAuthenticationFailure(request, response, exception);
        }
    }
}
