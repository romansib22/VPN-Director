package ru.rs.vpndirector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.vpndirector.config.OpenVpnProperties;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateGenerationService {

    private final OpenVpnProperties openVpnProperties;

    /**
     * Выпускает новый сертификат для клиента
     *
     * @param certificateName имя сертификата (CN)
     * @return результат выполнения команды
     */
    public String generateCertificate(String certificateName) {
        try {
            String easyRsaPath = openVpnProperties.getEasyRsaPath();
            String keysDir = easyRsaPath + "/keys";
            
            // Проверяем, существует ли уже сертификат с таким именем
            Path csrFile = Paths.get(keysDir, certificateName + ".csr");
            Path keyFile = Paths.get(keysDir, certificateName + ".key");
            Path crtFile = Paths.get(keysDir, certificateName + ".crt");
            if (Files.exists(csrFile) || Files.exists(keyFile) || Files.exists(crtFile)) {
                return "Ошибка: Сертификат с именем '" + certificateName + "' уже существует";
            }

            // Выполняем команды в правильной последовательности
            // 1. Переходим в директорию и загружаем vars
            // 2. Выполняем build-key с автоматическими ответами через stdin
            // build-key задает 10 вопросов (все Enter для значений по умолчанию),
            // затем 2 вопроса "Sign the certificate? [y/n]" (y) и "commit? [y/n]" (y)
            
            // Отключаем буферизацию вывода для более надежной работы
            String command = String.format(
                "cd %s && . %s/vars && stdbuf -oL -eL ./build-key %s",
                easyRsaPath, easyRsaPath, certificateName
            );
            
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            final PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
            
            StringBuilder output = new StringBuilder();
            log.info("Генерация сертификата {}...", certificateName);
            
            // Используем отдельный поток для отправки ответов с задержками
            // Этот поток будет отправлять ответы автоматически через определенные интервалы
            Thread answerThread = new Thread(() -> {
                try {
                    // Даем команде время на запуск и генерацию ключей
                    Thread.sleep(3000);
                    
                    // Отправляем 10 пустых строк для ответов на вопросы (значения по умолчанию)
                    for (int i = 0; i < 10; i++) {
                        writer.println();
                        writer.flush();
                        Thread.sleep(400);
                    }
                    
                    // Ждем появления вопросов о подписи
                    Thread.sleep(3000);
                    
                    // Отправляем 'y' для "Sign the certificate?"
                    writer.println("y");
                    writer.flush();
                    
                    // Ждем следующего вопроса
                    Thread.sleep(2000);
                    
                    // Отправляем 'y' для "commit?"
                    writer.println("y");
                    writer.flush();
                    
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Поток отправки ответов прерван");
                }
            });
            
            answerThread.setDaemon(true);
            answerThread.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("build-key output: {}", line);
                }
            } finally {
                // Даем потоку время завершиться
                try {
                    answerThread.join(15000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                writer.close();
            }

            int exitCode = process.waitFor();

            // Проверяем наличие созданных файлов
            boolean csrExists = Files.exists(csrFile);
            boolean keyExists = Files.exists(keyFile);
            boolean crtExists = Files.exists(crtFile);

            if (exitCode == 0 && csrExists && keyExists && crtExists) {
                log.info("Сертификат {} успешно создан. Файлы: {}.csr, {}.key, {}.crt",
                    certificateName, certificateName, certificateName, certificateName);
                return "Сертификат '" + certificateName + "' успешно создан! Файлы " + 
                    certificateName + ".csr, " + certificateName + ".key  и " + certificateName + ".crt созданы.";
            } else {
                String errorMsg = "Ошибка при создании сертификата.";
                if (exitCode != 0) {
                    errorMsg += " Код выхода: " + exitCode;
                }
                if (!csrExists) {
                    errorMsg += " Файл " + certificateName + ".csr не найден.";
                }
                if (!keyExists) {
                    errorMsg += " Файл " + certificateName + ".key не найден.";
                }
                if (!crtExists) {
                    errorMsg += " Файл " + certificateName + ".crt не найден.";
                }
                log.error("Ошибка при создании сертификата {}. {}", certificateName, errorMsg);
                return errorMsg + "\nВывод команды:\n" + output.toString();
            }
        } catch (Exception e) {
            log.error("Ошибка при генерации сертификата: {}", certificateName, e);
            return "Ошибка: " + e.getMessage();
        }
    }

    /**
     * Проверяет, существует ли сертификат с указанным именем
     */
    public boolean certificateExists(String certificateName) {
        String keysDir = openVpnProperties.getEasyRsaPath() + "/keys";
        Path crtFile = Paths.get(keysDir, certificateName + ".crt");
        return Files.exists(crtFile);
    }
}

