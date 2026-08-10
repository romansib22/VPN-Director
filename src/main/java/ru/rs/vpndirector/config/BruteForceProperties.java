package ru.rs.vpndirector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.bruteforce")
public class BruteForceProperties {
    /** Число неудачных попыток до блокировки IP */
    private int maxAttempts = 3;
    /** Длительность блокировки в минутах */
    private int lockMinutes = 15;
}
