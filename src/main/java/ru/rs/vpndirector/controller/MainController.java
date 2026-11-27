package ru.rs.vpndirector.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.rs.vpndirector.service.LoginAttemptService;
import ru.rs.vpndirector.util.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request, Model model) {
        // Для /login?blocked — сколько минут осталось до снятия блокировки с этого IP
        model.addAttribute("blockedMinutes",
            loginAttemptService.getRemainingLockMinutes(clientIpResolver.resolve(request)));
        return "login";
    }
}
