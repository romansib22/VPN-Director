package ru.rs.vpndirector.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.rs.vpndirector.config.BruteForceProperties;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Определяет IP клиента для счётчиков перебора пароля.
 * <p>
 * Заголовкам X-Real-IP / X-Forwarded-For доверяем только если запрос пришёл от доверенного прокси
 * (nginx на 127.0.0.1) — иначе клиент подставил бы их сам и выбирал, каким IP его считать.
 * nginx с $proxy_add_x_forwarded_for дописывает адрес клиента ПОСЛЕДНИМ элементом X-Forwarded-For,
 * поэтому берём его, а не первый (первый может поставить клиент).
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    /** Быстрая отсечка мусора перед разбором IPv6-литерала */
    private static final String IPV6 = "[0-9a-fA-F:.]+";

    private final BruteForceProperties properties;

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String realIp = parseIp(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String[] parts = forwarded.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = parseIp(parts[i]);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        String remote = parseIp(remoteAddr);
        if (remote == null || properties.getTrustedProxies() == null) {
            return false;
        }
        for (String proxy : properties.getTrustedProxies()) {
            if (remote.equals(parseIp(proxy))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return адрес в каноническом виде либо null, если это не IP-литерал. Имена не резолвятся,
     *         иначе подделанным заголовком можно было бы вызвать запрос к DNS.
     */
    static String parseIp(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            return null;
        }
        try {
            if (candidate.indexOf(':') >= 0) {
                // IPv6 в скобках трактуется строго как литерал (без DNS)
                return candidate.matches(IPV6) ? InetAddress.getByName("[" + candidate + "]").getHostAddress() : null;
            }
            // IPv4: четыре октета 0–255
            String[] octets = candidate.split("\\.", -1);
            if (octets.length != 4) {
                return null;
            }
            byte[] address = new byte[4];
            for (int i = 0; i < 4; i++) {
                String octet = octets[i];
                if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit)) {
                    return null;
                }
                int number = Integer.parseInt(octet);
                if (number > 255) {
                    return null;
                }
                address[i] = (byte) number;
            }
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
