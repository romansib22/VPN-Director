package ru.rs.vpndirector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.vpndirector.config.OpenVpnProperties;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenVpnStatusService {

    private final OpenVpnProperties openVpnProperties;
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");

    /**
     * Читает файл статуса OpenVPN
     */
    private List<String> readStatusFile() throws IOException {
        String statusFilePath = openVpnProperties.getOpenvpnRoot() + "/openvpn-status1194.log";
        Path statusPath = Paths.get(statusFilePath);
        
        if (!Files.exists(statusPath)) {
            log.warn("Файл статуса не существует: {}", statusPath);
            throw new IOException("Файл статуса не найден: " + statusPath);
        }
        
        // Определяем кодировку (аналогично OpenVpnConfigFileService)
        Charset charset = determineEncoding(statusPath);
        
        try {
            return Files.readAllLines(statusPath, charset);
        } catch (Exception e) {
            log.warn("Ошибка при чтении с кодировкой {}, пробуем UTF-8", charset.name());
            return Files.readAllLines(statusPath, StandardCharsets.UTF_8);
        }
    }

    /**
     * Определяет кодировку файла
     */
    private Charset determineEncoding(Path filePath) {
        if (openVpnProperties.getConfigEncoding() != null && !openVpnProperties.getConfigEncoding().trim().isEmpty()) {
            try {
                return Charset.forName(openVpnProperties.getConfigEncoding().trim());
            } catch (Exception e) {
                log.warn("Не удалось использовать указанную кодировку, используем UTF-8");
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Конвертирует время в московское время
     * Предполагается, что входное время в UTC или локальном времени сервера
     */
    private String convertToMoscowTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return timeStr;
        }
        
        try {
            // Парсим время (предполагаем формат "yyyy-MM-dd HH:mm:ss")
            LocalDateTime localDateTime = LocalDateTime.parse(timeStr.trim(), INPUT_FORMATTER);
            
            // Предполагаем, что время в UTC (OpenVPN обычно логирует в UTC)
            // Конвертируем в московское время
            ZonedDateTime utcTime = localDateTime.atZone(ZoneId.of("UTC"));
            ZonedDateTime moscowTime = utcTime.withZoneSameInstant(MOSCOW_ZONE);
            
            // Форматируем обратно в строку
            return moscowTime.format(OUTPUT_FORMATTER);
        } catch (DateTimeParseException e) {
            // Если не удалось распарсить, возвращаем исходную строку
            log.warn("Не удалось распарсить время: {}", timeStr);
            return timeStr;
        } catch (Exception e) {
            log.warn("Ошибка при конвертации времени в московское: {}", timeStr, e);
            return timeStr;
        }
    }

    /**
     * Вычисляет длительность подключения в формате "Д дней, ЧЧ часов ММ минут"
     */
    private String calculateDuration(String connectedSinceStr) {
        if (connectedSinceStr == null || connectedSinceStr.trim().isEmpty()) {
            return "-";
        }
        
        try {
            // Парсим время подключения (в UTC)
            LocalDateTime connectedSince = LocalDateTime.parse(connectedSinceStr.trim(), INPUT_FORMATTER);
            ZonedDateTime connectedSinceZoned = connectedSince.atZone(ZoneId.of("UTC"));
            
            // Текущее время в московском часовом поясе
            ZonedDateTime now = ZonedDateTime.now(MOSCOW_ZONE);
            
            // Конвертируем время подключения в московское для расчета
            ZonedDateTime connectedSinceMoscow = connectedSinceZoned.withZoneSameInstant(MOSCOW_ZONE);
            
            // Вычисляем разницу
            Duration duration = Duration.between(connectedSinceMoscow, now);
            
            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            long minutes = duration.toMinutes() % 60;
            
            return String.format("%d дней, %02d часов %02d минут", days, hours, minutes);
        } catch (Exception e) {
            log.warn("Ошибка при вычислении длительности подключения: {}", connectedSinceStr, e);
            return "-";
        }
    }

    /**
     * Парсит файл статуса и возвращает информацию о подключениях
     */
    public StatusInfo parseStatusFile() throws IOException {
        List<String> lines = readStatusFile();
        
        StatusInfo statusInfo = new StatusInfo();
        boolean inClientList = false;
        
        for (String line : lines) {
            line = line.trim();
            
            // Парсим строку Updated
            if (line.startsWith("Updated,")) {
                String updatedStr = line.substring(8).trim();
                String moscowTime = convertToMoscowTime(updatedStr);
                statusInfo.setLastUpdate(moscowTime);
                continue;
            }
            
            // Начало секции CLIENT LIST
            if (line.equals("OpenVPN CLIENT LIST")) {
                inClientList = true;
                continue;
            }
            
            // Конец секции CLIENT LIST
            if (line.equals("ROUTING TABLE")) {
                break;
            }
            
            // Пропускаем заголовок таблицы
            if (line.equals("Common Name,Real Address,Bytes Received,Bytes Sent,Connected Since")) {
                continue;
            }
            
            // Парсим строки подключений
            if (inClientList && !line.isEmpty() && line.contains(",")) {
                ClientConnection connection = parseConnectionLine(line);
                if (connection != null) {
                    statusInfo.addConnection(connection);
                }
            }
        }
        
        return statusInfo;
    }

    /**
     * Парсит строку подключения
     * Формат: kocmoc,46.39.231.140:14067,12689039,25101420,2025-12-16 14:18:32
     */
    private ClientConnection parseConnectionLine(String line) {
        try {
            String[] parts = line.split(",");
            if (parts.length < 5) {
                return null;
            }
            
            ClientConnection connection = new ClientConnection();
            connection.setClientName(parts[0].trim());
            
            // Извлекаем IP адрес из формата "46.39.231.140:14067"
            String realAddress = parts[1].trim();
            if (realAddress.contains(":")) {
                connection.setClientIp(realAddress.substring(0, realAddress.indexOf(":")));
            } else {
                connection.setClientIp(realAddress);
            }
            
            // Время подключения
            if (parts.length >= 5) {
                String connectedSince = parts[4].trim();
                String moscowTime = convertToMoscowTime(connectedSince);
                connection.setConnectedSince(moscowTime);
                
                // Вычисляем длительность подключения
                String duration = calculateDuration(connectedSince);
                connection.setDuration(duration);
            }
            
            return connection;
        } catch (Exception e) {
            log.warn("Ошибка при парсинге строки подключения: {}", line, e);
            return null;
        }
    }

    /**
     * Класс для хранения информации о статусе
     */
    public static class StatusInfo {
        private String lastUpdate;
        private List<ClientConnection> connections = new ArrayList<>();

        public String getLastUpdate() {
            return lastUpdate;
        }

        public void setLastUpdate(String lastUpdate) {
            this.lastUpdate = lastUpdate;
        }

        public List<ClientConnection> getConnections() {
            return connections;
        }

        public void addConnection(ClientConnection connection) {
            this.connections.add(connection);
        }
    }

    /**
     * Класс для хранения информации о подключении клиента
     */
    public static class ClientConnection {
        private String clientName;
        private String clientIp;
        private String connectedSince;
        private String duration;

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
        }

        public String getClientIp() {
            return clientIp;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }

        public String getConnectedSince() {
            return connectedSince;
        }

        public void setConnectedSince(String connectedSince) {
            this.connectedSince = connectedSince;
        }

        public String getDuration() {
            return duration;
        }

        public void setDuration(String duration) {
            this.duration = duration;
        }
    }
}

