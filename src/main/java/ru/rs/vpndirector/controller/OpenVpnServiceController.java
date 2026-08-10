package ru.rs.vpndirector.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.rs.vpndirector.config.OpenVpnProperties;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OpenVpnServiceController {
    
    private final OpenVpnProperties openVpnProperties;

    @GetMapping("/restart")
    public String restartPage(Model model) {
        return "restart";
    }

    @PostMapping("/restart")
    public String restartOpenVpn(RedirectAttributes redirectAttributes) {
        try {
            String serviceName = openVpnProperties.getSystemdServiceName();
            log.info("Перезапуск OpenVPN сервиса: systemctl restart {}", serviceName);

            ProcessBuilder processBuilder = new ProcessBuilder("sudo", "systemctl", "restart", serviceName);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                redirectAttributes.addFlashAttribute("success",
                    "OpenVPN успешно перезапущен (" + serviceName + ")!");
            } else {
                String details = output.toString().trim();
                String message = "Ошибка при перезапуске " + serviceName + ". Код выхода: " + exitCode;
                if (!details.isEmpty()) {
                    message += ". " + details;
                }
                log.warn("{}", message);
                redirectAttributes.addFlashAttribute("error", message);
            }
        } catch (Exception e) {
            log.error("Ошибка при перезапуске OpenVPN", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage() + 
                ". Убедитесь, что у приложения есть права на выполнение systemctl.");
        }
        
        return "redirect:/restart";
    }
}

