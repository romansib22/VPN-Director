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
            // Получаем имя файла конфигурации без расширения для systemctl
            String configName = openVpnProperties.getConfigFileNameWithoutExtension();
            String serviceName = "openvpn@" + configName;
            log.info("Перезапуск OpenVPN сервиса: {}", serviceName);
            
            // Попытка перезапустить OpenVPN через systemctl
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
                    "OpenVPN успешно перезапущен!");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Ошибка при перезапуске OpenVPN. Код выхода: " + exitCode);
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

