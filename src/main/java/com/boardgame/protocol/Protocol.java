package com.boardgame.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Protocol {
    private Protocol() {
    }

    public static String encode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
