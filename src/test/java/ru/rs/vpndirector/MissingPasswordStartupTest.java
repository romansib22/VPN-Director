package ru.rs.vpndirector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Без пароля веб-интерфейса приложение обязано отказаться стартовать: с пустым паролем
 * проверка BCrypt пропускала бы любой ввод.
 */
class MissingPasswordStartupTest {

    @Test
    void applicationRefusesToStartWithoutPassword() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> new SpringApplicationBuilder(VpndirectorApplication.class)
            // логирование и баннер — до инициализации контекста (иначе в вывод теста попадёт стектрейс ожидаемой ошибки)
            .properties("logging.level.root=OFF", "spring.main.banner-mode=off")
            // пароль — с высшим приоритетом, чтобы переменная окружения машины не повлияла на тест
            .initializers(context -> context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "security.user.password", "",
                "server.port", "0"))))
            .run());

        String messages = messageChain(failure);
        assertTrue(messages.contains("Не задан пароль веб-интерфейса"), messages);
    }

    private static String messageChain(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage()).append('\n');
            if (cause.getCause() == cause) {
                break;
            }
        }
        return messages.toString();
    }
}
