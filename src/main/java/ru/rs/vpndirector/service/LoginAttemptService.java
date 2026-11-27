package ru.rs.vpndirector.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.rs.vpndirector.config.BruteForceProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LoginAttemptService {

    /** После этого числа записей удаляем устаревшие, чтобы карта не росла бесконечно при сканировании с разных IP */
    private static final int PURGE_THRESHOLD = 10_000;

    private final BruteForceProperties properties;
    private final Clock clock;
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(BruteForceProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    LoginAttemptService(BruteForceProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isBlocked(String ip) {
        AttemptState state = attempts.get(ip);
        if (state == null || state.blockedUntil == null) {
            return false;
        }
        if (clock.instant().isBefore(state.blockedUntil)) {
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
        long seconds = Duration.between(clock.instant(), state.blockedUntil).getSeconds();
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
        Instant now = clock.instant();
        Duration window = Duration.ofMinutes(Math.max(1, properties.getFailureWindowMinutes()));
        AttemptState state = attempts.compute(ip, (key, existing) -> {
            AttemptState current = existing != null ? existing : new AttemptState();
            if (current.blockedUntil != null) {
                if (now.isBefore(current.blockedUntil)) {
                    return current;
                }
                current.blockedUntil = null;
            }
            // Неудачи считаются только внутри окна; более старые не накапливаются днями
            if (current.firstFailure == null || current.firstFailure.plus(window).isBefore(now)) {
                current.failures = 0;
            }
            if (current.failures == 0) {
                current.firstFailure = now;
            }
            current.failures++;
            if (current.failures >= properties.getMaxAttempts()) {
                current.blockedUntil = clock.instant().plus(Duration.ofMinutes(properties.getLockMinutes()));
                current.failures = 0;
                current.firstFailure = null;
                log.warn("IP {} заблокирован на {} мин. после {} неудачных попыток входа",
                    ip, properties.getLockMinutes(), properties.getMaxAttempts());
            }
            return current;
        });
        purgeStale(now, window);
        return state.blockedUntil != null && now.isBefore(state.blockedUntil);
    }

    private void purgeStale(Instant now, Duration window) {
        if (attempts.size() < PURGE_THRESHOLD) {
            return;
        }
        attempts.values().removeIf(state -> isStale(state, now, window));
    }

    private boolean isStale(AttemptState state, Instant now, Duration window) {
        boolean blocked = state.blockedUntil != null && now.isBefore(state.blockedUntil);
        if (blocked) {
            return false;
        }
        return state.firstFailure == null || state.firstFailure.plus(window).isBefore(now);
    }

    private static final class AttemptState {
        private int failures;
        private Instant firstFailure;
        private Instant blockedUntil;
    }
}
