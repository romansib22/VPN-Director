package ru.rs.vpndirector.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.rs.vpndirector.service.OpenVpnConfigFileService;

import java.net.InetAddress;

@Slf4j
@Controller
@RequiredArgsConstructor
public class IpManagementController {

    private final OpenVpnConfigFileService configFileService;

    @GetMapping("/add-ip")
    public String addIpPage(Model model) {
        return "add-ip";
    }

    @PostMapping("/add-ip")
    public String addIp(@RequestParam String ipAddress, 
                        @RequestParam(required = false) String comment,
                        RedirectAttributes redirectAttributes) {
        try {
            // Валидация IP адреса
            InetAddress.getByName(ipAddress);
            
            // Преобразуем IP адрес в подсеть (например, 1.2.3.4 -> 1.2.3.0)
            String[] ipParts = ipAddress.split("\\.");
            if (ipParts.length != 4) {
                redirectAttributes.addFlashAttribute("error", "Неверный формат IP адреса");
                return "redirect:/add-ip";
            }
            
            String subnet = ipParts[0] + "." + ipParts[1] + "." + ipParts[2] + ".0";
            String routeLine = "push \"route " + subnet + " 255.255.255.0\"";
            
            // Проверяем, есть ли уже такая строка в файле
            String existingLine = configFileService.findLineContaining("push \"route " + subnet + " 255.255.255.0\"");
            
            if (existingLine != null) {
                // Строка уже существует
                redirectAttributes.addFlashAttribute("error", 
                    "Такой адрес уже есть: " + existingLine);
            } else {
                // Если комментарий не пустой, добавляем его перед строкой route
                if (comment != null && !comment.trim().isEmpty()) {
                    String commentLine = "#" + comment.trim();
                    configFileService.appendLine(commentLine);
                }
                
                // Добавляем строку маршрута
                configFileService.appendLine(routeLine);
                redirectAttributes.addFlashAttribute("success", "Подсеть успешно добавлена");
            }
        } catch (Exception e) {
            log.error("Ошибка при добавлении IP адреса", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }
        return "redirect:/add-ip";
    }

    @GetMapping("/add-ip-by-domain")
    public String addIpByDomainPage(Model model) {
        return "add-ip-by-domain";
    }

    @PostMapping("/add-ip-by-domain")
    public String addIpByDomain(@RequestParam String domain, RedirectAttributes redirectAttributes) {
        try {
            // Получаем IP адрес по домену
            InetAddress address = InetAddress.getByName(domain);
            String ipAddress = address.getHostAddress();
            
            // Проверяем наличие маршрута для конкретного IP адреса
            String exactRouteLine = "push \"route " + ipAddress + " 255.255.255.255\"";
            String existingExactRoute = configFileService.findLineContaining(exactRouteLine);
            
            if (existingExactRoute != null) {
                redirectAttributes.addFlashAttribute("error", 
                    "Такой IP адрес уже есть: " + existingExactRoute);
                return "redirect:/add-ip-by-domain";
            }
            
            // Преобразуем IP адрес в подсеть (например, 1.2.3.4 -> 1.2.3.0)
            String[] ipParts = ipAddress.split("\\.");
            if (ipParts.length != 4) {
                redirectAttributes.addFlashAttribute("error", "Неверный формат IP адреса");
                return "redirect:/add-ip-by-domain";
            }
            
            String subnet = ipParts[0] + "." + ipParts[1] + "." + ipParts[2] + ".0";
            String subnetRouteLine = "push \"route " + subnet + " 255.255.255.0\"";
            
            // Проверяем наличие маршрута для подсети
            String existingSubnetRoute = configFileService.findLineContaining(subnetRouteLine);
            
            if (existingSubnetRoute != null) {
                redirectAttributes.addFlashAttribute("error", 
                    "Подсеть для этого IP адреса уже есть: " + existingSubnetRoute);
                return "redirect:/add-ip-by-domain";
            }
            
            // Добавляем комментарий с доменом перед строкой маршрута
            String commentLine = "#" + domain;
            configFileService.appendLine(commentLine);
            
            // Добавляем IP в конфигурацию
            configFileService.appendLine(exactRouteLine);
            
            redirectAttributes.addFlashAttribute("success", 
                "IP адрес " + ipAddress + " для домена " + domain + " успешно добавлен!");
        } catch (Exception e) {
            log.error("Ошибка при добавлении IP по домену", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }
        return "redirect:/add-ip-by-domain";
    }
}

