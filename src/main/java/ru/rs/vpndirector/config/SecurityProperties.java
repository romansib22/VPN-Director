package ru.rs.vpndirector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.user")
public class SecurityProperties {
    private String name = "admin";
    /** Обязательный: значения по умолчанию нет, без пароля приложение не стартует */
    private String password;
}
