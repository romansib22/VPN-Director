package ru.rs.vpndirector.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import ru.rs.vpndirector.service.LoginAttemptService;
import ru.rs.vpndirector.util.ClientIpResolver;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;

    public LoginSuccessHandler(LoginAttemptService loginAttemptService, ClientIpResolver clientIpResolver) {
        this.loginAttemptService = loginAttemptService;
        this.clientIpResolver = clientIpResolver;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        loginAttemptService.loginSucceeded(clientIpResolver.resolve(request));
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
