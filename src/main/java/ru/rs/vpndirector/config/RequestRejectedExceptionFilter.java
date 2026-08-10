package ru.rs.vpndirector.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Страховочная обработка: если RequestRejectedException всё же «всплывёт»
 * из Spring Security, отвечаем 400 и не даём упасть обработке запроса с ERROR.
 * Сам процесс приложения при этом не останавливается.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestRejectedExceptionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RequestRejectedException ex) {
            handleRejected(request, response, ex);
        } catch (ServletException ex) {
            if (ex.getCause() instanceof RequestRejectedException) {
                handleRejected(request, response, (RequestRejectedException) ex.getCause());
            } else {
                throw ex;
            }
        }
    }

    private void handleRejected(HttpServletRequest request, HttpServletResponse response,
                                RequestRejectedException ex) throws IOException {
        log.warn("Отклонён подозрительный запрос: {} {} — {}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
