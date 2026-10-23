package dev.ytmlyrics.source;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Source-only transport: HTTPS, normal certificate checks, no accounts or cookies. */
final class SourceHttp {
    private SourceHttp() {}
    static String request(String url, String form, String referer) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) YtmLyrics/0.3.0");
        connection.setRequestProperty("Referer", referer);
        connection.setRequestProperty("Accept", "application/json,text/xml,text/plain");
        try {
            if (form != null) {
                byte[] bytes = form.getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (var output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status == 429) throw new IOException("请求过于频繁，请稍后重试");
            if (status != 200) throw new IOException("服务返回 HTTP " + status);
            try (InputStream input = connection.getInputStream(); var output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 2 * 1024 * 1024) throw new IOException("歌词响应过大");
                    output.write(buffer, 0, count);
                }
                return output.toString(StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
