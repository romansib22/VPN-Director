package ru.rs.vpndirector.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.rs.vpndirector.service.CertificateGenerationService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CertificateGenerationController {

    private final CertificateGenerationService certificateGenerationService;

    @GetMapping("/generate-certificate")
    public String generateCertificatePage(Model model) {
        return "generate-certificate";
    }

    @PostMapping("/generate-certificate")
    public String generateCertificate(@RequestParam String certificateName, RedirectAttributes redirectAttributes) {
        try {
            if (certificateName == null || certificateName.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Имя сертификата не может быть пустым");
                return "redirect:/generate-certificate";
            }

            certificateName = certificateName.trim();

            // Проверяем, существует ли уже сертификат
            if (certificateGenerationService.certificateExists(certificateName)) {
                redirectAttributes.addFlashAttribute("error", 
                    "Сертификат с именем '" + certificateName + "' уже существует");
                return "redirect:/generate-certificate";
            }

            // Генерируем сертификат
            String result = certificateGenerationService.generateCertificate(certificateName);
            
            if (result.startsWith("Ошибка:")) {
                redirectAttributes.addFlashAttribute("error", result);
            } else {
                redirectAttributes.addFlashAttribute("success", result);
            }
        } catch (Exception e) {
            log.error("Ошибка при генерации сертификата", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }
        
        return "redirect:/generate-certificate";
    }
}

