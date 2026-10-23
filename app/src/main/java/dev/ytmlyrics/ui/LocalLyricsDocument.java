package dev.ytmlyrics.ui;

import java.nio.charset.StandardCharsets;

/** Serves the bundled document without allowing a request to reach the network. */
final class LocalLyricsDocument {
    static final String URL = "https://ytm-lyrics.invalid/lyrics.html";
    record Response(String mimeType, int status, String reason, byte[] body) {}
    private final byte[] html;

    LocalLyricsDocument(String html) {
        this.html = html.getBytes(StandardCharsets.UTF_8);
    }

    Response request(String url, String method, boolean mainFrame) {
        if (mainFrame && "GET".equals(method) && URL.equals(url))
            return new Response("text/html", 200, "OK", html);
        return new Response("text/plain", 403, "Blocked", new byte[0]);
    }
}
