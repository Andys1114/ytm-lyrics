package dev.ytmlyrics.ui;

import java.nio.charset.StandardCharsets;

/** Serves the bundled document without allowing a request to reach the network. */
final class LocalLyricsDocument {
    static final String URL = "https://ytm-lyrics.invalid/lyrics.html";
    record Response(String mimeType, int status, String reason, byte[] body) {}
    private final byte[] html;
    private final String url;

    LocalLyricsDocument(String html) {
        this(URL, html);
    }

    LocalLyricsDocument(String url, String html) {
        this.url = url;
        this.html = html.getBytes(StandardCharsets.UTF_8);
    }

    Response request(String url, String method, boolean mainFrame) {
        if (mainFrame && "GET".equals(method) && this.url.equals(url))
            return new Response("text/html", 200, "OK", html);
        return new Response("text/plain", 403, "Blocked", new byte[0]);
    }
}
