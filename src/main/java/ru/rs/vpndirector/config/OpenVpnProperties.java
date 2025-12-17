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
    
    /**
     * Возвращает полный путь к файлу конфигурации
     */
    public String getConfigPath() {
        return openvpnRoot + "/" + configFileName;
    }
}

