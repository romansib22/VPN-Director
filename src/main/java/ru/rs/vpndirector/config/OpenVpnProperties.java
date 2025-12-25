package ru.rs.vpndirector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openvpn.server")
public class OpenVpnProperties {
    private String openvpnRoot = "/etc/openvpn";
    private String configFileName = "server.conf";
    private String configEncoding = null; // Явно указанная кодировка (если null - определяется автоматически)
    private String easyRsaPath = "/etc/openvpn/easy-rsa/2.0";
    private String statusFileName = "openvpn-status1194.log";
    
    /**
     * Возвращает полный путь к файлу конфигурации
     */
    public String getConfigPath() {
        return openvpnRoot + "/" + configFileName;
    }
    
    /**
     * Возвращает полный путь к файлу статуса
     */
    public String getStatusFilePath() {
        return openvpnRoot + "/" + statusFileName;
    }
    
    /**
     * Возвращает имя файла конфигурации без расширения
     * Например, "server.conf" -> "server"
     */
    public String getConfigFileNameWithoutExtension() {
        String fileName = configFileName;
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }
}

