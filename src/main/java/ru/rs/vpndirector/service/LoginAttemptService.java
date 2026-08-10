package ru.rs.vpndirector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.vpndirector.config.BruteForceProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final BruteForceProperties properties;
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        AttemptState state = attempts.get(ip);
        if (state == null || state.blockedUntil == null) {
            return false;
        }
        if (Instant.now().isBefore(state.blockedUntil)) {
            return true;
        }
        attempts.remove(ip);
        return false;
    }

    /**
     * Сколько минут осталось до снятия блокировки (минимум 1, если ещё заблокирован).
     */
    public long getRemainingLockMinutes(String ip) {
        AttemptState state = attempts.get(ip);
        if (state == null || state.blockedUntil == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), state.blockedUntil).getSeconds();
        if (seconds <= 0) {
            attempts.remove(ip);
            return 0;
        }
        return Math.max(1, (seconds + 59) / 60);
    }

    public void loginSucceeded(String ip) {
        attempts.remove(ip);
    }

    /**
     * @return true, если IP только что заблокирован (или уже был)
     */
    public boolean loginFailed(String ip) {
        AttemptState state = attempts.compute(ip, (key, existing) -> {
            AttemptState current = existing != null ? existing : new AttemptState();
            if (current.blockedUntil != null && Instant.now().isBefore(current.blockedUntil)) {
                return current;
            }
            current.blockedUntil = null;
            current.failures++;
            if (current.failures >= properties.getMaxAttempts()) {
                current.blockedUntil = Instant.now().plus(Duration.ofMinutes(properties.getLockMinutes()));
                current.failures = 0;
                log.warn("IP {} заблокирован на {} мин. после {} неудачных попыток входа",
                    ip, properties.getLockMinutes(), properties.getMaxAttempts());
            }
            return current;
        });
        return state.blockedUntil != null && Instant.now().isBefore(state.blockedUntil);
    }

    private static final class AttemptState {
        private int failures;
        private Instant blockedUntil;
    }
}
