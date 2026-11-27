package ru.rs.vpndirector.service;

import org.junit.jupiter.api.Test;
import ru.rs.vpndirector.config.BruteForceProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private static final String IP = "198.51.100.10";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
    private final BruteForceProperties properties = new BruteForceProperties(); // 3 попытки, блок 15 мин, окно 10 мин
    private final LoginAttemptService service = new LoginAttemptService(properties, clock);

    @Test
    void blocksIpAfterMaxAttempts() {
        assertFalse(service.loginFailed(IP));
        assertFalse(service.loginFailed(IP));
        assertTrue(service.loginFailed(IP));
        assertTrue(service.isBlocked(IP));
        assertEquals(15, service.getRemainingLockMinutes(IP));
    }

    @Test
    void failuresOfDifferentIpsAreIndependent() {
        service.loginFailed(IP);
        service.loginFailed(IP);
        service.loginFailed("198.51.100.11");
        assertFalse(service.isBlocked(IP));
        assertFalse(service.isBlocked("198.51.100.11"));
    }

    @Test
    void successResetsFailures() {
        service.loginFailed(IP);
        service.loginFailed(IP);
        service.loginSucceeded(IP);
        assertFalse(service.loginFailed(IP));
        assertFalse(service.loginFailed(IP));
        assertFalse(service.isBlocked(IP));
    }

    @Test
    void failuresInsideWindowAccumulate() {
        service.loginFailed(IP);
        clock.advance(Duration.ofMinutes(9));
        service.loginFailed(IP);
        clock.advance(Duration.ofMinutes(1));
        assertTrue(service.loginFailed(IP));
    }

    @Test
    void failuresOlderThanWindowAreForgotten() {
        service.loginFailed(IP);
        service.loginFailed(IP);
        clock.advance(Duration.ofMinutes(11));
        assertFalse(service.loginFailed(IP));
        assertFalse(service.loginFailed(IP));
        assertTrue(service.loginFailed(IP));
    }

    @Test
    void blockIsNotExtendedByFurtherFailures() {
        block();
        clock.advance(Duration.ofMinutes(5));
        assertTrue(service.loginFailed(IP));
        assertEquals(10, service.getRemainingLockMinutes(IP));
    }

    @Test
    void blockExpiresAfterLockMinutes() {
        block();
        clock.advance(Duration.ofMinutes(14));
        assertTrue(service.isBlocked(IP));
        assertEquals(1, service.getRemainingLockMinutes(IP));
        clock.advance(Duration.ofMinutes(1));
        assertFalse(service.isBlocked(IP));
        assertEquals(0, service.getRemainingLockMinutes(IP));
        // после снятия блокировки счёт неудач начинается заново
        assertFalse(service.loginFailed(IP));
        assertFalse(service.isBlocked(IP));
    }

    @Test
    void singleAttemptModeBlocksImmediately() {
        properties.setMaxAttempts(1);
        assertTrue(service.loginFailed(IP));
        assertTrue(service.isBlocked(IP));
    }

    private void block() {
        service.loginFailed(IP);
        service.loginFailed(IP);
        service.loginFailed(IP);
    }

    /** Часы, которые переводит тест */
    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
