package ru.rs.vpndirector.util;

import java.util.regex.Pattern;

/**
 * Проверка имён, которые попадают из запроса в пути файлов и в командную строку.
 */
public final class SafeNames {

    /** Имя сертификата (CN): буквы, цифры, точка, дефис, подчёркивание; первый символ — буква или цифра */
    private static final Pattern CERTIFICATE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private SafeNames() {
    }

    /**
     * Строгая проверка имени сертификата перед выпуском: имя подставляется в команду bash
     * и становится именем файлов .key/.csr/.crt.
     */
    public static boolean isCertificateNameValid(String name) {
        return name != null && CERTIFICATE_NAME.matcher(name).matches();
    }

    /**
     * Проверка имени, которое используется только для поиска файла внутри каталога:
     * без разделителей путей, без "."/"..", без управляющих символов.
     */
    public static boolean isPathSegmentValid(String name) {
        if (name == null || name.isBlank() || name.length() > 255) {
            return false;
        }
        if (name.equals(".") || name.equals("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }
}
