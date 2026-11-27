package ru.rs.vpndirector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.bruteforce")
public class BruteForceProperties {
    /** Число неудачных попыток до блокировки IP */
    private int maxAttempts = 3;
    /** Длительность блокировки в минутах */
    private int lockMinutes = 15;
    /** Окно (в минутах), внутри которого неудачные попытки суммируются; более старые сбрасываются */
    private int failureWindowMinutes = 10;
    /** Адреса прокси, которым доверяем X-Real-IP / X-Forwarded-For (приложение должно быть доступно только через них) */
    private List<String> trustedProxies = new ArrayList<>(List.of("127.0.0.1", "::1"));
}
