package ru.rs.vpndirector.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rs.vpndirector.config.OpenVpnProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CertificateController {

    private final OpenVpnProperties openVpnProperties;
    private static final Pattern CN_PATTERN = Pattern.compile("/CN=([^/]+)");

    @GetMapping("/certificates")
    public String certificatesPage(Model model) {
        List<CertificateInfo> certificates = new ArrayList<>();
        
        try {
            String keysDir = openVpnProperties.getEasyRsaPath() + "/keys";
            Path indexFile = Paths.get(keysDir, "index.txt");
            if (Files.exists(indexFile)) {
                List<String> lines = Files.readAllLines(indexFile, StandardCharsets.UTF_8);
                
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    // Парсим строку формата: V	270111231001Z		02	unknown	/C=RU/ST=CFO/L=Moscow/O=rs/OU=MyOrganizationalUnit/CN=rstablet/name=EasyRSA/emailAddress=roman@romansib.ru
                    String certificateName = extractCertificateName(line);
                    if (certificateName != null && !certificateName.isEmpty()) {
                        boolean hasFiles = checkCertificateFiles(certificateName, keysDir);
                        certificates.add(new CertificateInfo(certificateName, hasFiles));
                    }
                }
            } else {
                log.warn("Файл index.txt не найден: {}", indexFile);
            }
        } catch (IOException e) {
            log.error("Ошибка при чтении файла index.txt", e);
        }
        
        model.addAttribute("certificates", certificates);
        return "certificates";
    }

    /**
     * Извлекает имя сертификата (CN) из строки index.txt
     */
    private String extractCertificateName(String line) {
        try {
            Matcher matcher = CN_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            log.warn("Ошибка при извлечении имени сертификата из строки: {}", line, e);
        }
        return null;
    }

    /**
     * Проверяет наличие файлов .crt и .key для сертификата
     */
    private boolean checkCertificateFiles(String certificateName, String keysDir) {
        try {
            Path crtFile = Paths.get(keysDir, certificateName + ".crt");
            Path keyFile = Paths.get(keysDir, certificateName + ".key");
            
            return Files.exists(crtFile) && Files.exists(keyFile);
        } catch (Exception e) {
            log.warn("Ошибка при проверке файлов сертификата: {}", certificateName, e);
            return false;
        }
    }

    /**
     * Скачивание конфигурации клиента для тоннеля
     */
    @PostMapping("/certificates/download/tunnel")
    public ResponseEntity<Resource> downloadTunnelConfig(@RequestParam String certificateName) {
        return generateAndDownloadConfig(certificateName, "client_template_tun.ovpn", "_tun");
    }

    /**
     * Скачивание конфигурации клиента для маршрутов
     */
    @PostMapping("/certificates/download/routes")
    public ResponseEntity<Resource> downloadRoutesConfig(@RequestParam String certificateName) {
        return generateAndDownloadConfig(certificateName, "client_template.ovpn", "");
    }

    /**
     * Генерирует конфигурацию клиента из шаблона и отдает на скачивание
     */
    private ResponseEntity<Resource> generateAndDownloadConfig(String certificateName, String templateFileName, String filenameSuffix) {
        try {
            String openvpnRoot = openVpnProperties.getOpenvpnRoot();
            String easyRsaPath = openVpnProperties.getEasyRsaPath();
            String keysDir = easyRsaPath + "/keys";
            
            // Читаем шаблон
            Path templatePath = Paths.get(openvpnRoot, templateFileName);
            if (!Files.exists(templatePath)) {
                log.error("Шаблон не найден: {}", templatePath);
                return ResponseEntity.notFound().build();
            }
            
            String template = new String(Files.readAllBytes(templatePath), StandardCharsets.UTF_8);
            
            // Читаем содержимое файлов сертификата и ключа
            Path keyFile = Paths.get(keysDir, certificateName + ".key");
            Path certFile = Paths.get(keysDir, certificateName + ".crt");
            
            if (!Files.exists(keyFile) || !Files.exists(certFile)) {
                log.error("Файлы сертификата не найдены для: {}", certificateName);
                return ResponseEntity.notFound().build();
            }
            
            String keyContent = new String(Files.readAllBytes(keyFile), StandardCharsets.UTF_8);
            String certContent = new String(Files.readAllBytes(certFile), StandardCharsets.UTF_8);
            
            // Заменяем плейсхолдеры в шаблоне
            String configContent = template
                .replace("{key}", keyContent)
                .replace("{cert}", certContent);
            
            // Создаем ресурс из байтов
            byte[] configBytes = configContent.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(configBytes);
            
            String filename = certificateName + filenameSuffix + ".ovpn";
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
                
        } catch (IOException e) {
            log.error("Ошибка при генерации конфигурации для сертификата: {}", certificateName, e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Неожиданная ошибка при генерации конфигурации для сертификата: {}", certificateName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Класс для хранения информации о сертификате
     */
    public static class CertificateInfo {
        private String name;
        private boolean hasFiles;

        public CertificateInfo(String name, boolean hasFiles) {
            this.name = name;
            this.hasFiles = hasFiles;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isHasFiles() {
            return hasFiles;
        }

        public void setHasFiles(boolean hasFiles) {
            this.hasFiles = hasFiles;
        }
    }
}

