package ru.rs.vpndirector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZoneOffset;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * Смещение от UTC в часах (например 3 для UTC+3)
     */
    private int serverTimezone = 3;

    /**
     * Часовой пояс для отображения даты/времени в интерфейсе
     */
    public ZoneId getDisplayZoneId() {
        return ZoneId.ofOffset("UTC", ZoneOffset.ofHours(serverTimezone));
    }
}
