package ru.rs.vpndirector.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Controller
public class OpenVpnServiceController {

    @GetMapping("/restart")
    public String restartPage(Model model) {
        return "restart";
    }

    @PostMapping("/restart")
    public String restartOpenVpn(RedirectAttributes redirectAttributes) {
        try {
            // Попытка перезапустить OpenVPN через systemctl
            ProcessBuilder processBuilder = new ProcessBuilder("sudo", "systemctl", "restart", "openvpn@server");
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

