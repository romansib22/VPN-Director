package ru.rs.vpndirector;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сценарии безопасности через настоящий HTTP-сервер: CSRF, блокировка перебора, определение IP клиента,
 * проверка имён файлов. Сервер видит клиента как 127.0.0.1 (доверенный прокси), поэтому каждый клиент
 * теста задаёт свой X-Real-IP — так счётчики разных тестов не пересекаются.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    private static final String PASSWORD = "integration-pass";
    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final Path OPENVPN_ROOT = createOpenvpnRoot();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.user.password", () -> PASSWORD);
        registry.add("openvpn.server.openvpn-root", () -> OPENVPN_ROOT.toString());
        registry.add("openvpn.server.easy-rsa-path", () -> OPENVPN_ROOT.resolve("easy-rsa/2.0").toString());
        registry.add("security.bruteforce.max-attempts", () -> "3");
        registry.add("security.bruteforce.lock-minutes", () -> "15");
    }

    private static Path createOpenvpnRoot() {
        try {
            Path root = Files.createTempDirectory("vpndirector-test");
            Path clients = Files.createDirectories(root.resolve("clients"));
            Files.writeString(clients.resolve("client1.ovpn"), "client1 config\n");
            Path keys = Files.createDirectories(root.resolve("easy-rsa/2.0/keys"));
            Files.writeString(keys.resolve("client1.key"), "KEY\n");
            Files.writeString(keys.resolve("client1.crt"), "CERT\n");
            Files.writeString(root.resolve("client_template.ovpn"), "client\n{key}\n{cert}\n");
            Files.writeString(root.resolve("client_template_tun.ovpn"), "client tun\n{key}\n{cert}\n");
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void anonymousIsRedirectedToLogin() {
        Client client = new Client("203.0.113.1", null);
        HttpResponse<String> response = client.get("/");
        assertEquals(302, response.statusCode());
        assertTrue(client.location(response).endsWith("/login"), client.location(response));
    }

    @Test
    void loginPageWithAllMessagesIsPublic() {
        Client client = new Client("203.0.113.2", null);
        assertEquals(200, client.get("/login?error").statusCode());
        assertTrue(client.get("/login?error").body().contains("Неверное имя пользователя или пароль"));
        assertTrue(client.get("/login?expired").body().contains("Сессия истекла"));
        assertTrue(client.get("/login?blocked").body().contains("Слишком много неудачных попыток"));
        assertTrue(client.get("/login?logout").body().contains("Вы успешно вышли"));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() {
        Client client = new Client("203.0.113.3", null);
        HttpResponse<String> response = client.post("/login", "username=admin&password=" + encode(PASSWORD));
        assertEquals(302, response.statusCode());
        assertTrue(client.location(response).endsWith("/login?expired"), client.location(response));
    }

    @Test
    void logoutWithoutCsrfTokenIsForbidden() {
        Client client = authenticated("203.0.113.4");
        assertEquals(403, client.post("/logout", "").statusCode());
    }

    @Test
    void bruteForceBlocksIpEvenWithCorrectPassword() {
        Client client = new Client("203.0.113.5", null);
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertTrue(client.location(client.login("wrong")).endsWith("/login?error"));
        }
        assertTrue(client.location(client.login("wrong")).endsWith("/login?blocked"));
        assertTrue(client.location(client.login(PASSWORD)).endsWith("/login?blocked"));
        String page = client.get("/login?blocked").body();
        assertTrue(page.matches("(?s).*Повторите через\\s*<span[^>]*>15</span>.*"), page);
        // другой IP не затронут
        assertTrue(authenticated("203.0.113.6").get("/").body().contains("VPN Director"));
    }

    @Test
    void spoofedForwardedForDoesNotHideBlockedIp() {
        // nginx дописывает адрес клиента последним элементом X-Forwarded-For; первый может подставить клиент
        Client victim = new Client(null, "6.6.6.6, 203.0.113.7");
        for (int attempt = 1; attempt <= 3; attempt++) {
            victim.login("wrong");
        }
        // реальный адрес (последний элемент) заблокирован, хотя «клиент» называет себя 6.6.6.6
        assertTrue(victim.location(victim.login(PASSWORD)).endsWith("/login?blocked"));
        // другой клиент, подставляющий первым элементом чужой адрес, проходит под своим реальным адресом
        Client other = new Client(null, "203.0.113.7, 203.0.113.8");
        String location = other.location(other.login(PASSWORD));
        assertTrue(location.matches(".*/$"), location);
    }

    @Test
    void configDownloadRejectsPathTraversal() {
        Client client = authenticated("203.0.113.9");
        assertEquals(404, downloadConfig(client, "../secret.txt").statusCode());
        assertEquals(404, downloadConfig(client, "sub/../../client1.ovpn").statusCode());
        assertEquals(404, downloadConfig(client, "client1").statusCode());
        HttpResponse<String> ok = downloadConfig(client, "client1.ovpn");
        assertEquals(200, ok.statusCode());
        assertEquals("client1 config\n", ok.body());
    }

    @Test
    void certificateNameIsValidated() {
        Client client = authenticated("203.0.113.10");
        String token = client.csrfToken();
        HttpResponse<String> rejected = client.post("/certificates/download/tunnel",
            "certificateName=" + encode("../../escape") + "&_csrf=" + encode(token));
        assertEquals(400, rejected.statusCode());
        HttpResponse<String> ok = client.post("/certificates/download/tunnel",
            "certificateName=" + encode("client1") + "&_csrf=" + encode(token));
        assertEquals(200, ok.statusCode());
    }

    private static HttpResponse<String> downloadConfig(Client client, String filename) {
        return client.post("/download-config", "filename=" + encode(filename) + "&_csrf=" + encode(client.csrfToken()));
    }

    private Client authenticated(String ip) {
        Client client = new Client(ip, null);
        HttpResponse<String> response = client.login(PASSWORD);
        assertEquals(302, response.statusCode());
        assertTrue(client.location(response).matches(".*/$"), client.location(response));
        return client;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Клиент со своей сессией. realIp — что прокси подставляет в X-Real-IP (null — заголовка нет),
     * forwardedFor — что пришло в X-Forwarded-For.
     */
    private final class Client {

        private final String realIp;
        private final String forwardedFor;
        private final CookieManager cookies = new CookieManager();
        private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        Client(String realIp, String forwardedFor) {
            this.realIp = realIp;
            this.forwardedFor = forwardedFor;
        }

        HttpResponse<String> get(String path) {
            return send(HttpRequest.newBuilder(uri(path)).GET());
        }

        HttpResponse<String> post(String path, String form) {
            return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
        }

        HttpResponse<String> login(String password) {
            return post("/login", "username=admin&password=" + encode(password) + "&_csrf=" + encode(csrfToken()));
        }

        String csrfToken() {
            Matcher matcher = CSRF_FIELD.matcher(get("/login").body());
            assertTrue(matcher.find(), "на странице входа нет поля _csrf");
            return matcher.group(1);
        }

        String location(HttpResponse<?> response) {
            return response.headers().firstValue("Location").orElse("");
        }

        private HttpResponse<String> send(HttpRequest.Builder builder) {
            if (realIp != null) {
                builder.header("X-Real-IP", realIp);
            }
            if (forwardedFor != null) {
                builder.header("X-Forwarded-For", forwardedFor);
            }
            try {
                return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
