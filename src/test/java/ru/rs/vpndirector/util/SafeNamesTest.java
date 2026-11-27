package ru.rs.vpndirector.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeNamesTest {

    @Test
    void plainCertificateNamesAreAccepted() {
        assertTrue(SafeNames.isCertificateNameValid("client1"));
        assertTrue(SafeNames.isCertificateNameValid("client-1_v2.test"));
        assertTrue(SafeNames.isCertificateNameValid("A"));
        assertTrue(SafeNames.isCertificateNameValid("a".repeat(64)));
    }

    @Test
    void unsafeCertificateNamesAreRejected() {
        assertFalse(SafeNames.isCertificateNameValid(null));
        assertFalse(SafeNames.isCertificateNameValid(""));
        assertFalse(SafeNames.isCertificateNameValid("-client"));
        assertFalse(SafeNames.isCertificateNameValid(".hidden"));
        assertFalse(SafeNames.isCertificateNameValid(".."));
        assertFalse(SafeNames.isCertificateNameValid("a b"));
        assertFalse(SafeNames.isCertificateNameValid("a;b"));
        assertFalse(SafeNames.isCertificateNameValid("$(id)"));
        assertFalse(SafeNames.isCertificateNameValid("`id`"));
        assertFalse(SafeNames.isCertificateNameValid("../../etc"));
        assertFalse(SafeNames.isCertificateNameValid("a/b"));
        assertFalse(SafeNames.isCertificateNameValid("a\\b"));
        assertFalse(SafeNames.isCertificateNameValid("a\n"));
        assertFalse(SafeNames.isCertificateNameValid("клиент"));
        assertFalse(SafeNames.isCertificateNameValid("a".repeat(65)));
    }

    @Test
    void pathSegmentValidation() {
        assertTrue(SafeNames.isPathSegmentValid("client1.ovpn"));
        assertTrue(SafeNames.isPathSegmentValid("клиент.conf"));
        assertTrue(SafeNames.isPathSegmentValid("a b.ovpn"));
        assertTrue(SafeNames.isPathSegmentValid("..client.ovpn"));

        assertFalse(SafeNames.isPathSegmentValid(null));
        assertFalse(SafeNames.isPathSegmentValid(""));
        assertFalse(SafeNames.isPathSegmentValid("  "));
        assertFalse(SafeNames.isPathSegmentValid("."));
        assertFalse(SafeNames.isPathSegmentValid(".."));
        assertFalse(SafeNames.isPathSegmentValid("../escape.txt"));
        assertFalse(SafeNames.isPathSegmentValid("a/b"));
        assertFalse(SafeNames.isPathSegmentValid("a\\b"));
        assertFalse(SafeNames.isPathSegmentValid("a\0b"));
        assertFalse(SafeNames.isPathSegmentValid("a\nb"));
        assertFalse(SafeNames.isPathSegmentValid("x".repeat(256)));
    }
}
