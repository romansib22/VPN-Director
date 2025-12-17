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

import java.io.IOException;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ConfigEditorController {

    private final OpenVpnConfigFileService configFileService;

    @GetMapping("/editor")
    public String editor(Model model) {
        try {
            String filePath = configFileService.getConfigFilePath();
            model.addAttribute("filePath", filePath);
            model.addAttribute("backupExists", configFileService.backupExists());
            model.addAttribute("defaultFileExists", configFileService.defaultFileExists());
            
            if (configFileService.configFileExists()) {
                // Определяем кодировку файла
                String encoding = configFileService.detectFileEncoding();
                model.addAttribute("fileEncoding", encoding != null ? encoding : "не определена");
                
                List<String> lines = configFileService.readConfigFile();
                String content = String.join("\n", lines);
                model.addAttribute("content", content);
                model.addAttribute("fileExists", true);
                
                log.info("Файл прочитан. Определенная кодировка: {}", encoding);
            } else {
                model.addAttribute("fileExists", false);
                model.addAttribute("content", "");
                model.addAttribute("fileEncoding", "файл не существует");
            }
        } catch (IOException e) {
            log.error("Ошибка при чтении файла конфигурации", e);
            model.addAttribute("error", "Ошибка при чтении файла: " + e.getMessage());
            model.addAttribute("fileExists", false);
            model.addAttribute("content", "");
            model.addAttribute("filePath", configFileService.getConfigFilePath());
            model.addAttribute("fileEncoding", "ошибка чтения");
            model.addAttribute("backupExists", false);
            model.addAttribute("defaultFileExists", false);
        }
        return "editor";
    }

    @PostMapping("/editor/save")
    public String saveConfig(@RequestParam String content, RedirectAttributes redirectAttributes) {
        try {
            List<String> lines = List.of(content.split("\n"));
            configFileService.writeConfigFile(lines);
            redirectAttributes.addFlashAttribute("success", "Файл успешно сохранен! Создана резервная копия.");
        } catch (IOException e) {
            log.error("Ошибка при сохранении файла конфигурации", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при сохранении файла: " + e.getMessage());
        }
        return "redirect:/editor";
    }

    @PostMapping("/editor/restore")
    public String restoreFromBackup(RedirectAttributes redirectAttributes) {
        try {
            configFileService.restoreFromBackup();
            redirectAttributes.addFlashAttribute("success", "Файл успешно восстановлен из резервной копии!");
        } catch (IOException e) {
            log.error("Ошибка при восстановлении файла из резервной копии", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при восстановлении: " + e.getMessage());
        }
        return "redirect:/editor";
    }

    @PostMapping("/editor/reset")
    public String resetToDefault(RedirectAttributes redirectAttributes) {
        try {
            configFileService.resetToDefault();
            redirectAttributes.addFlashAttribute("success", "Конфигурация успешно сброшена к значениям по умолчанию!");
        } catch (IOException e) {
            log.error("Ошибка при сбросе конфигурации", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при сбросе: " + e.getMessage());
        }
        return "redirect:/editor";
    }
}

