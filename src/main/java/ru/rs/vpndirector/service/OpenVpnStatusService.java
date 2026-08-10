package ru.rs.vpndirector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.vpndirector.config.AppProperties;
import ru.rs.vpndirector.config.OpenVpnProperties;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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
    private final AppProperties appProperties;
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    /** Индекс поля Connected Since (читаемая дата) в строке CLIENT_LIST CSV v3 */
    private static final int CSV_V3_FIELD_CONNECTED_SINCE = 7;
    /** Индекс поля Connected Since (time_t, unix) в строке CLIENT_LIST CSV v3 */
    private static final int CSV_V3_FIELD_CONNECTED_SINCE_UNIX = 8;

    private enum StatusFileFormat {
        LEGACY,
        CSV_V3
    }

    /**
     * Читает файл статуса OpenVPN
     */
    private List<String> readStatusFile() throws IOException {
        String statusFilePath = openVpnProperties.getStatusFilePath();
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

    private ZoneId displayZone() {
        return appProperties.getDisplayZoneId();
    }

    /**
     * Конвертирует время из UTC (legacy status) в настроенный часовой пояс
     */
    private String convertUtcToDisplayTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return timeStr;
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(timeStr.trim(), INPUT_FORMATTER);
            ZonedDateTime utcTime = localDateTime.atZone(ZoneId.of("UTC"));
            return utcTime.withZoneSameInstant(displayZone()).format(OUTPUT_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Не удалось распарсить время: {}", timeStr);
            return timeStr;
        } catch (Exception e) {
            log.warn("Ошибка при конвертации времени: {}", timeStr, e);
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
            
            ZonedDateTime now = ZonedDateTime.now(displayZone());
            ZonedDateTime connectedSinceDisplay = connectedSinceZoned.withZoneSameInstant(displayZone());
            Duration duration = Duration.between(connectedSinceDisplay, now);
            return formatDuration(duration);
        } catch (Exception e) {
            log.warn("Ошибка при вычислении длительности подключения: {}", connectedSinceStr, e);
            return "-";
        }
    }

    /**
     * Форматирует дату/время из CSV v3 (локальное время сервера) в dd.MM.yyyy HH:mm:ss
     */
    private String formatCsvV3DateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return dateTimeStr;
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(dateTimeStr.trim(), INPUT_FORMATTER);
            return localDateTime.atZone(displayZone()).format(OUTPUT_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Не удалось распарсить время CSV v3: {}", dateTimeStr);
            return dateTimeStr;
        }
    }

    /**
     * Длительность подключения по unix time (поле Connected Since (time_t) в CSV v3)
     */
    private String calculateDurationFromUnix(String unixTimeStr) {
        if (unixTimeStr == null || unixTimeStr.trim().isEmpty()) {
            return "-";
        }
        try {
            long epochSeconds = Long.parseLong(unixTimeStr.trim());
            Duration duration = Duration.between(Instant.ofEpochSecond(epochSeconds), Instant.now());
            if (duration.isNegative()) {
                duration = Duration.ZERO;
            }
            return formatDuration(duration);
        } catch (NumberFormatException e) {
            log.warn("Некорректный unix time: {}", unixTimeStr);
            return "-";
        }
    }

    /**
     * Длительность по читаемой дате CSV v3 (интерпретация в настроенном часовом поясе)
     */
    private String calculateDurationFromLocalDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return "-";
        }
        try {
            LocalDateTime connectedSince = LocalDateTime.parse(dateTimeStr.trim(), INPUT_FORMATTER);
            ZonedDateTime connectedZoned = connectedSince.atZone(displayZone());
            Duration duration = Duration.between(connectedZoned, ZonedDateTime.now(displayZone()));
            if (duration.isNegative()) {
                duration = Duration.ZERO;
            }
            return formatDuration(duration);
        } catch (Exception e) {
            log.warn("Ошибка при вычислении длительности CSV v3: {}", dateTimeStr, e);
            return "-";
        }
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        return String.format("%d дней, %02d часов %02d минут", days, hours, minutes);
    }

    /**
     * Парсит файл статуса и возвращает информацию о подключениях
     */
    public StatusInfo parseStatusFile() throws IOException {
        List<String> lines = readStatusFile();
        StatusFileFormat format = detectFormat(lines);
        log.debug("Формат файла статуса OpenVPN: {}", format);
        return format == StatusFileFormat.CSV_V3 ? parseCsvV3(lines) : parseLegacy(lines);
    }

    private StatusFileFormat detectFormat(List<String> lines) {
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("CLIENT_LIST,") || line.startsWith("HEADER,CLIENT_LIST")) {
                return StatusFileFormat.CSV_V3;
            }
            if (line.equals("OpenVPN CLIENT LIST") || line.startsWith("Updated,")) {
                return StatusFileFormat.LEGACY;
            }
        }
        return StatusFileFormat.LEGACY;
    }

    /**
     * Парсит формат OpenVPN 2.5+ (status-version 3, CSV)
     */
    private StatusInfo parseCsvV3(List<String> lines) {
        StatusInfo statusInfo = new StatusInfo();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.equals("END")) {
                continue;
            }

            if (line.startsWith("TIME,")) {
                String[] parts = line.split(",", 3);
                if (parts.length >= 2) {
                    statusInfo.setLastUpdate(formatCsvV3DateTime(parts[1].trim()));
                }
                continue;
            }

            if (!line.startsWith("CLIENT_LIST,")) {
                continue;
            }

            ClientConnection connection = parseCsvV3ConnectionLine(line);
            if (connection != null) {
                statusInfo.addConnection(connection);
            }
        }

        return statusInfo;
    }

    /**
     * CLIENT_LIST,name,realAddr,virtualAddr,,bytesRx,bytesTx,connectedSince,connectedSinceUnix,...
     */
    private ClientConnection parseCsvV3ConnectionLine(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length <= CSV_V3_FIELD_CONNECTED_SINCE) {
            return null;
        }
        try {
            ClientConnection connection = new ClientConnection();
            connection.setClientName(parts[1].trim());

            String realAddress = parts[2].trim();
            int colon = realAddress.indexOf(':');
            connection.setClientIp(colon > 0 ? realAddress.substring(0, colon) : realAddress);

            String connectedSinceHuman = parts[CSV_V3_FIELD_CONNECTED_SINCE].trim();
            connection.setConnectedSince(formatCsvV3DateTime(connectedSinceHuman));

            if (parts.length > CSV_V3_FIELD_CONNECTED_SINCE_UNIX) {
                String connectedSinceUnix = parts[CSV_V3_FIELD_CONNECTED_SINCE_UNIX].trim();
                if (!connectedSinceUnix.isEmpty() && connectedSinceUnix.chars().allMatch(Character::isDigit)) {
                    connection.setDuration(calculateDurationFromUnix(connectedSinceUnix));
                } else {
                    connection.setDuration(calculateDurationFromLocalDateTime(connectedSinceHuman));
                }
            } else {
                connection.setDuration(calculateDurationFromLocalDateTime(connectedSinceHuman));
            }
            return connection;
        } catch (Exception e) {
            log.warn("Ошибка при парсинге CLIENT_LIST: {}", line, e);
            return null;
        }
    }

    /**
     * Парсит legacy-формат (OpenVPN до 2.5)
     */
    private StatusInfo parseLegacy(List<String> lines) {
        StatusInfo statusInfo = new StatusInfo();
        boolean inClientList = false;

        for (String raw : lines) {
            String line = raw.trim();

            if (line.startsWith("Updated,")) {
                String updatedStr = line.substring(8).trim();
                statusInfo.setLastUpdate(convertUtcToDisplayTime(updatedStr));
                continue;
            }

            if (line.equals("OpenVPN CLIENT LIST")) {
                inClientList = true;
                continue;
            }

            if (line.equals("ROUTING TABLE")) {
                break;
            }

            if (line.equals("Common Name,Real Address,Bytes Received,Bytes Sent,Connected Since")) {
                continue;
            }

            if (inClientList && !line.isEmpty() && line.contains(",")) {
                ClientConnection connection = parseLegacyConnectionLine(line);
                if (connection != null) {
                    statusInfo.addConnection(connection);
                }
            }
        }

        return statusInfo;
    }

    /**
     * Парсит строку подключения (legacy)
     * Формат: kocmoc,46.39.231.140:14067,12689039,25101420,2025-12-16 14:18:32
     */
    private ClientConnection parseLegacyConnectionLine(String line) {
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
                String displayTime = convertUtcToDisplayTime(connectedSince);
                connection.setConnectedSince(displayTime);
                
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

