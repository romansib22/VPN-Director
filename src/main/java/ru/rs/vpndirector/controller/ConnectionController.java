package ru.rs.vpndirector.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.rs.vpndirector.service.OpenVpnStatusService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ConnectionController {

    private final OpenVpnStatusService statusService;

    @GetMapping("/connections")
    public String connectionsPage(Model model) {
        try {
            OpenVpnStatusService.StatusInfo statusInfo = statusService.parseStatusFile();
            model.addAttribute("statusInfo", statusInfo);
            model.addAttribute("hasError", false);
        } catch (Exception e) {
            log.error("Ошибка при чтении файла статуса", e);
            model.addAttribute("error", "Ошибка при чтении файла статуса: " + e.getMessage());
            model.addAttribute("hasError", true);
        }
        return "connections";
    }
}

