package ru.rs.vpndirector.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rs.vpndirector.config.OpenVpnProperties;
import ru.rs.vpndirector.util.SafeNames;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ClientConfigController {

    private final OpenVpnProperties openVpnProperties;

    @GetMapping("/download-config")
    public String downloadConfigPage(Model model) {
        List<String> configs = new ArrayList<>();
        
        try {
            // Ищем конфигурационные файлы клиентов
            String clientsDirPath = openVpnProperties.getOpenvpnRoot() + "/clients";
            Path clientsDir = Paths.get(clientsDirPath);
            if (Files.exists(clientsDir) && Files.isDirectory(clientsDir)) {
                try (Stream<Path> paths = Files.list(clientsDir)) {
                    paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".ovpn") || p.toString().endsWith(".conf"))
                        .forEach(path -> configs.add(path.getFileName().toString()));
                }
            }
            
            if (configs.isEmpty()) {
                configs.add("Конфигурационные файлы не найдены");
            }
        } catch (IOException e) {
            log.error("Ошибка при чтении списка конфигураций", e);
            configs.add("Ошибка при чтении: " + e.getMessage());
        }
        
        model.addAttribute("configs", configs);
        return "download-config";
    }

    @PostMapping("/download-config")
    public ResponseEntity<Resource> downloadConfig(@RequestParam String filename) {
        try {
            if (!SafeNames.isPathSegmentValid(filename) || !isConfigFile(filename)) {
                return ResponseEntity.notFound().build();
            }

            Path clientsDir = Paths.get(openVpnProperties.getOpenvpnRoot(), "clients").toAbsolutePath().normalize();
            // normalize + startsWith: файл обязан лежать внутри каталога clients (без ../)
            Path filePath = clientsDir.resolve(filename).normalize();
            if (!filePath.startsWith(clientsDir) || !Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(resource);
        } catch (Exception e) {
            log.error("Ошибка при скачивании конфигурации", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static boolean isConfigFile(String filename) {
        return filename.endsWith(".ovpn") || filename.endsWith(".conf");
    }
}

