package org.webrobot.cli.utils;

import java.util.Random;

public class Utils {

    static final Random random = new Random(); // Or SecureRandom
    static final int startChar = (int) '!';
    static final int endChar = (int) '~';

    public static String randomString(final int maxLength) {
        final int length = random.nextInt(maxLength + 1);
        return random.ints(length, startChar, endChar + 1)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
