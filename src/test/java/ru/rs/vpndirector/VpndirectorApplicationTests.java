package ru.rs.vpndirector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    // Пароль обязателен: без него контекст не стартует
    "security.user.password=test-password"
})
class VpndirectorApplicationTests {

    @Test
    void contextLoads() {
    }

}
