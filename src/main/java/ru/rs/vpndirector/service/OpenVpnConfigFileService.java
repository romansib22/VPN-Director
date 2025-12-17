package ru.rs.vpndirector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.vpndirector.config.OpenVpnProperties;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenVpnConfigFileService {

    private final OpenVpnProperties openVpnProperties;

    /**
     * Читает содержимое файла конфигурации
     *
     * @return список строк файла
     * @throws IOException если произошла ошибка при чтении файла
     */
    public List<String> readConfigFile() throws IOException {
        Path configPath = Paths.get(openVpnProperties.getConfigPath());
        log.info("Чтение файла конфигурации: {}", configPath);
        
        if (!Files.exists(configPath)) {
            log.warn("Файл не существует: {}", configPath);
            throw new IOException("Файл конфигурации не найден: " + configPath);
        }
        
        // Определяем кодировку файла
        String detectedEncoding = detectFileEncoding();
        Charset charset;
        
        if (detectedEncoding != null) {
            try {
                charset = Charset.forName(detectedEncoding);
            } catch (Exception e) {
                log.warn("Не удалось создать Charset для {}, используем Windows-1251", detectedEncoding);
                charset = Charset.forName("Windows-1251");
            }
        } else {
            // Если не удалось определить, пробуем в порядке вероятности
            charset = Charset.forName("Windows-1251");
        }
        
        byte[] fileBytes = Files.readAllBytes(configPath);
        
        // Сначала пробуем определенную кодировку
        try {
            String content = new String(fileBytes, charset);
            List<String> lines = new ArrayList<>(Arrays.asList(content.split("\\r?\\n")));
            
            // Проверяем на наличие символа замены
            if (!content.contains("\uFFFD") && !content.chars().anyMatch(c -> c == 0xFFFD)) {
                log.info("Файл успешно прочитан с кодировкой: {}", charset.name());
                return lines;
            }
        } catch (Exception e) {
            log.debug("Не удалось прочитать файл с определенной кодировкой {}: {}", charset.name(), e.getMessage());
        }
        
        // Если определенная кодировка не подошла, пробуем другие в порядке вероятности
        Charset[] fallbackCharsets = {
            StandardCharsets.UTF_8,
            Charset.forName("Windows-1251"),
            Charset.forName("KOI8-R"),
            Charset.forName("CP866"),
            StandardCharsets.ISO_8859_1
        };
        
        for (Charset fallbackCharset : fallbackCharsets) {
            if (fallbackCharset.equals(charset)) {
                continue; // Уже пробовали
            }
            try {
                String content = new String(fileBytes, fallbackCharset);
                List<String> lines = new ArrayList<>(Arrays.asList(content.split("\\r?\\n")));
                
                if (!content.contains("\uFFFD") && !content.chars().anyMatch(c -> c == 0xFFFD)) {
                    log.info("Файл успешно прочитан с кодировкой: {}", fallbackCharset.name());
                    return lines;
                }
            } catch (Exception e) {
                // Пробуем следующую кодировку
            }
        }
        
        // В крайнем случае используем Windows-1251
        log.warn("Не удалось прочитать файл с другими кодировками, используем Windows-1251");
        String content = new String(fileBytes, Charset.forName("Windows-1251"));
        return new ArrayList<>(Arrays.asList(content.split("\\r?\\n")));
    }

    /**
     * Записывает содержимое в файл конфигурации
     *
     * @param lines список строк для записи
     * @throws IOException если произошла ошибка при записи файла
     */
    public void writeConfigFile(List<String> lines) throws IOException {
        Path configPath = Paths.get(openVpnProperties.getConfigPath());
        log.info("Запись файла конфигурации: {}", configPath);
        
        // Создаем резервную копию перед сохранением, если файл существует
        if (Files.exists(configPath)) {
            Path backupPath = Paths.get(openVpnProperties.getOpenvpnRoot(), 
                openVpnProperties.getConfigFileName() + "_bak");
            try {
                Files.copy(configPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.info("Создана резервная копия: {}", backupPath);
            } catch (Exception e) {
                log.warn("Не удалось создать резервную копию: {}", e.getMessage());
            }
        }
        
        // Создаем родительские директории, если они не существуют
        if (configPath.getParent() != null) {
            Files.createDirectories(configPath.getParent());
        }
        
        // Определяем кодировку для записи
        Charset writeCharset;
        if (openVpnProperties.getConfigEncoding() != null && !openVpnProperties.getConfigEncoding().trim().isEmpty()) {
            // Используем явно указанную кодировку
            try {
                writeCharset = Charset.forName(openVpnProperties.getConfigEncoding().trim());
                log.info("Запись файла с явно указанной кодировкой: {}", writeCharset.name());
            } catch (Exception e) {
                log.warn("Не удалось использовать указанную кодировку {}, используем Windows-1251", 
                    openVpnProperties.getConfigEncoding());
                writeCharset = Charset.forName("Windows-1251");
            }
        } else {
            // Определяем кодировку из существующего файла или используем Windows-1251 по умолчанию
            String detectedEncoding = detectFileEncoding();
            if (detectedEncoding != null) {
                try {
                    writeCharset = Charset.forName(detectedEncoding);
                    log.info("Запись файла с определенной кодировкой: {}", writeCharset.name());
                } catch (Exception e) {
                    log.warn("Не удалось использовать определенную кодировку {}, используем Windows-1251", detectedEncoding);
                    writeCharset = Charset.forName("Windows-1251");
                }
            } else {
                // По умолчанию используем Windows-1251 для файлов с кириллицей
                writeCharset = Charset.forName("Windows-1251");
                log.info("Запись файла с кодировкой по умолчанию: {}", writeCharset.name());
            }
        }
        
        // Записываем файл в определенной кодировке
        // Используем CharsetEncoder с обработкой ошибок для безопасной записи
        try {
            CharsetEncoder encoder = writeCharset.newEncoder();
            encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
            encoder.onMalformedInput(CodingErrorAction.REPLACE);
            
            // Обрабатываем строки для безопасной записи
            List<String> safeLines = new ArrayList<>();
            for (String line : lines) {
                try {
                    // Проверяем, можно ли закодировать строку
                    if (encoder.canEncode(line)) {
                        safeLines.add(line);
                    } else {
                        // Заменяем проблемные символы
                        log.warn("Строка содержит невалидные символы для кодировки {}, заменяем: {}", 
                            writeCharset.name(), line);
                        String safeLine = line.replaceAll("[^\\x00-\\x7F]", "?");
                        safeLines.add(safeLine);
                    }
                } catch (Exception e) {
                    // Если не удалось проверить, заменяем проблемные символы
                    log.warn("Ошибка при проверке строки, заменяем проблемные символы: {}", line);
                    String safeLine = line.replaceAll("[^\\x00-\\x7F]", "?");
                    safeLines.add(safeLine);
                }
            }
            
            // Записываем файл
            Files.write(configPath, safeLines, writeCharset, 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            log.info("Файл успешно записан: {}", configPath);
        } catch (Exception e) {
            log.error("Ошибка при записи файла с кодировкой {}, пробуем UTF-8", writeCharset.name(), e);
            // В крайнем случае пробуем записать в UTF-8
            try {
                Files.write(configPath, lines, StandardCharsets.UTF_8, 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                log.info("Файл записан в UTF-8");
            } catch (Exception e2) {
                log.error("Критическая ошибка при записи файла", e2);
                throw new IOException("Не удалось записать файл: " + e2.getMessage(), e2);
            }
        }
    }

    /**
     * Заменяет строку в файле, если она начинается с указанного префикса
     *
     * @param prefix префикс строки для поиска (например, "port")
     * @param newValue новое значение строки (например, "port 1194")
     * @return true если замена была выполнена, false если строка не найдена
     * @throws IOException если произошла ошибка при чтении/записи файла
     */
    public boolean replaceLineByPrefix(String prefix, String newValue) throws IOException {
        List<String> lines = readConfigFile();
        boolean replaced = false;
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith(prefix)) {
                lines.set(i, newValue);
                replaced = true;
                log.info("Заменена строка '{}' на '{}'", line, newValue);
                break;
            }
        }
        
        if (replaced) {
            writeConfigFile(lines);
        } else {
            log.warn("Строка с префиксом '{}' не найдена", prefix);
        }
        
        return replaced;
    }

    /**
     * Добавляет новую строку в конец файла
     *
     * @param line строка для добавления
     * @throws IOException если произошла ошибка при чтении/записи файла
     */
    public void appendLine(String line) throws IOException {
        List<String> lines = readConfigFile();
        lines.add(line);
        writeConfigFile(lines);
        log.info("Добавлена строка: {}", line);
    }

    /**
     * Удаляет строку из файла по префиксу
     *
     * @param prefix префикс строки для удаления
     * @return true если строка была удалена, false если не найдена
     * @throws IOException если произошла ошибка при чтении/записи файла
     */
    public boolean removeLineByPrefix(String prefix) throws IOException {
        List<String> lines = readConfigFile();
        boolean removed = lines.removeIf(line -> line.trim().startsWith(prefix));
        
        if (removed) {
            writeConfigFile(lines);
            log.info("Удалена строка с префиксом: {}", prefix);
        } else {
            log.warn("Строка с префиксом '{}' не найдена для удаления", prefix);
        }
        
        return removed;
    }

    /**
     * Получает значение параметра из файла конфигурации
     *
     * @param key ключ параметра (например, "port")
     * @return значение параметра или null, если не найдено
     * @throws IOException если произошла ошибка при чтении файла
     */
    public String getConfigValue(String key) throws IOException {
        List<String> lines = readConfigFile();
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key)) {
                // Извлекаем значение после ключа
                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length > 1) {
                    return parts[1].trim();
                }
            }
        }
        
        return null;
    }

    /**
     * Проверяет существование файла конфигурации
     *
     * @return true если файл существует
     */
    public boolean configFileExists() {
        Path configPath = Paths.get(openVpnProperties.getConfigPath());
        return Files.exists(configPath);
    }

    /**
     * Получает путь к файлу конфигурации
     *
     * @return путь к файлу конфигурации
     */
    public String getConfigFilePath() {
        return openVpnProperties.getConfigPath();
    }

    /**
     * Ищет строку в файле, содержащую указанный текст
     *
     * @param searchText текст для поиска
     * @return найденная строка или null, если не найдена
     * @throws IOException если произошла ошибка при чтении файла
     */
    public String findLineContaining(String searchText) throws IOException {
        List<String> lines = readConfigFile();
        for (String line : lines) {
            if (line.contains(searchText)) {
                return line;
            }
        }
        return null;
    }

    /**
     * Проверяет, является ли массив байтов валидным UTF-8
     */
    private boolean isValidUTF8(byte[] bytes) {
        try {
            String test = new String(bytes, StandardCharsets.UTF_8);
            // Проверяем, что при обратном преобразовании получаем те же байты
            byte[] back = test.getBytes(StandardCharsets.UTF_8);
            if (back.length != bytes.length) {
                return false;
            }
            for (int i = 0; i < bytes.length; i++) {
                if (back[i] != bytes[i]) {
                    return false;
                }
            }
            // Проверяем отсутствие символа замены
            return !test.contains("\uFFFD");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Определяет кодировку файла конфигурации
     *
     * @return название кодировки или null, если не удалось определить
     */
    public String detectFileEncoding() {
        Path configPath = Paths.get(openVpnProperties.getConfigPath());
        
        if (!Files.exists(configPath)) {
            log.warn("Файл не существует: {}", configPath);
            return null;
        }
        
        // Если кодировка явно указана в конфигурации, используем её
        if (openVpnProperties.getConfigEncoding() != null && !openVpnProperties.getConfigEncoding().trim().isEmpty()) {
            String explicitEncoding = openVpnProperties.getConfigEncoding().trim();
            log.info("Используется явно указанная кодировка из конфигурации: {}", explicitEncoding);
            return explicitEncoding;
        }
        
        try {
            byte[] fileBytes = Files.readAllBytes(configPath);
            
            // Сначала проверяем UTF-8 - это наиболее вероятная кодировка для Linux файлов
            if (isValidUTF8(fileBytes)) {
                String utf8Content = new String(fileBytes, StandardCharsets.UTF_8);
                // Проверяем наличие кириллицы в UTF-8
                boolean hasCyrillicUTF8 = utf8Content.chars().anyMatch(c -> c >= 0x0400 && c <= 0x04FF);
                if (hasCyrillicUTF8) {
                    log.info("Определена кодировка файла: UTF-8 (найдена кириллица)");
                    return "UTF-8";
                } else {
                    log.info("Определена кодировка файла: UTF-8");
                    return "UTF-8";
                }
            }
            
            // Если не UTF-8, пробуем кириллические кодировки в порядке вероятности
            // Windows-1251 - наиболее распространенная для кириллицы в Windows/Linux
            // Приоритет: Windows-1251 (наиболее вероятна для файлов с кириллицей)
            Charset[] cyrillicCharsets = {
                Charset.forName("Windows-1251"),  // Высший приоритет для кириллицы
                Charset.forName("KOI8-R"),
                Charset.forName("CP866")
            };
            
            String bestMatch = null;
            int bestScore = 0;
            
            for (Charset charset : cyrillicCharsets) {
                try {
                    String content = new String(fileBytes, charset);
                    
                    // Проверяем на наличие символа замены
                    if (content.contains("\uFFFD") || content.chars().anyMatch(c -> c == 0xFFFD)) {
                        continue;
                    }
                    
                    // Подсчитываем количество валидных кириллических символов
                    long cyrillicCount = content.chars()
                        .filter(c -> (c >= 0x0410 && c <= 0x044F) || // Основная кириллица
                                    (c >= 0x0401 && c <= 0x0451) || // Ё, ё
                                    (c >= 0x0400 && c <= 0x04FF))   // Вся кириллица
                        .count();
                    
                    // Подсчитываем количество печатных ASCII символов (буквы, цифры, знаки препинания)
                    long asciiCount = content.chars()
                        .filter(c -> c >= 0x20 && c <= 0x7E)
                        .count();
                    
                    // Если найдена кириллица и достаточно ASCII символов - это хороший кандидат
                    if (cyrillicCount > 0 && (asciiCount + cyrillicCount) > content.length() * 0.8) {
                        int score = (int)(cyrillicCount * 10 + asciiCount);
                        if (score > bestScore) {
                            bestScore = score;
                            bestMatch = charset.name();
                        }
                    }
                } catch (Exception e) {
                    // Пробуем следующую кодировку
                }
            }
            
            if (bestMatch != null) {
                log.info("Определена кодировка файла: {} (найдена кириллица, score: {})", bestMatch, bestScore);
                return bestMatch;
            }
            
            // Если не удалось определить точно, пробуем через системную утилиту
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("file", "-bi", configPath.toString());
                Process process = processBuilder.start();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
                String output = reader.readLine();
                if (output != null && output.contains("charset=")) {
                    String encoding = output.substring(output.indexOf("charset=") + 8).trim();
                    log.info("Определена кодировка через утилиту file: {}", encoding);
                    return encoding;
                }
            } catch (Exception e) {
                log.debug("Не удалось определить кодировку через утилиту file: {}", e.getMessage());
            }
            
            // По умолчанию для Linux файлов с кириллицей - Windows-1251
            log.warn("Не удалось точно определить кодировку файла, используем Windows-1251 по умолчанию");
            return "Windows-1251";
        } catch (IOException e) {
            log.error("Ошибка при определении кодировки файла", e);
            return null;
        }
    }

    /**
     * Восстанавливает файл конфигурации из резервной копии
     *
     * @throws IOException если произошла ошибка при восстановлении
     */
    public void restoreFromBackup() throws IOException {
        Path configPath = Paths.get(openVpnProperties.getConfigPath());
        Path backupPath = Paths.get(openVpnProperties.getOpenvpnRoot(), 
            openVpnProperties.getConfigFileName() + "_bak");
        
        if (!Files.exists(backupPath)) {
            throw new IOException("Резервная копия не найдена: " + backupPath);
        }
        
        Files.copy(backupPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("Файл восстановлен из резервной копии: {}", backupPath);
    }

    /**
     * Проверяет наличие резервной копии
     *
     * @return true если резервная копия существует
     */
    public boolean backupExists() {
        Path backupPath = Paths.get(openVpnProperties.getOpenvpnRoot(), 
            openVpnProperties.getConfigFileName() + "_bak");
        return Files.exists(backupPath);
    }

    /**
     * Сбрасывает конфигурацию к значениям по умолчанию
     *
     * @throws IOException если произошла ошибка при сбросе
     */
    public void resetToDefault() throws IOException {
        Path configPath = Paths.get(openVpnProperties.getConfigPath());
        Path defaultPath = Paths.get(openVpnProperties.getOpenvpnRoot(), 
            openVpnProperties.getConfigFileName() + "_default");
        
        if (!Files.exists(defaultPath)) {
            throw new IOException("Файл по умолчанию не найден: " + defaultPath);
        }
        
        // Создаем резервную копию текущего файла перед сбросом
        if (Files.exists(configPath)) {
            Path backupPath = Paths.get(openVpnProperties.getOpenvpnRoot(), 
                openVpnProperties.getConfigFileName() + "_bak");
            try {
                Files.copy(configPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.info("Создана резервная копия перед сбросом: {}", backupPath);
            } catch (Exception e) {
                log.warn("Не удалось создать резервную копию перед сбросом: {}", e.getMessage());
            }
        }
        
        Files.copy(defaultPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("Конфигурация сброшена к значениям по умолчанию из: {}", defaultPath);
    }

    /**
     * Проверяет наличие файла по умолчанию
     *
     * @return true если файл по умолчанию существует
     */
    public boolean defaultFileExists() {
        Path defaultPath = Paths.get(openVpnProperties.getOpenvpnRoot(), 
            openVpnProperties.getConfigFileName() + "_default");
        return Files.exists(defaultPath);
    }
}

