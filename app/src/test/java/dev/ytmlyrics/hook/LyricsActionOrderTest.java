package dev.ytmlyrics.hook;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class LyricsActionOrderTest {
    private static final byte[] REACTIONS = field(4, field(10, field(484586709, number(8, 1))));
    private static final byte[] COMMENTS = field(4, field(10, field(457313787, number(8, 1))));
    private static final byte[] SHARE = button(field(2, new byte[] {1, 2, 3}));
    private static final byte[] NATIVE_LYRICS = button(join(label("Lyrics"), field(9, field(138681778, new byte[] {1}))));
    private static final byte[] TOAST = field(6, field(76623563, field(1, new byte[] {2, 3, 4, 5, 6})));
    private static final byte[] FALLBACK = button(join(label("Lyrics"), TOAST));

    @Test public void fallbackMovesAfterReactionsWithoutChangingAnyButtonBytes() {
        byte[] flag = number(8, 1);
        byte[] nativeBar = bar(REACTIONS, COMMENTS, flag, SHARE, FALLBACK);
        byte[] snapshot = nativeBar.clone();
        byte[] moved = move(nativeBar);
        assertArrayEquals(bar(REACTIONS, FALLBACK, flag, COMMENTS, SHARE), moved);
        assertArrayEquals(snapshot, nativeBar);
        assertSame(moved, move(moved));
    }

    @Test public void leavesNativeLyricsAndBarsWithoutAnUnambiguousAnchorAlone() {
        byte[] nativeLyrics = bar(REACTIONS, COMMENTS, NATIVE_LYRICS);
        assertSame(nativeLyrics, move(nativeLyrics));
        byte[] missingReactions = bar(COMMENTS, SHARE, FALLBACK);
        assertSame(missingReactions, move(missingReactions));
        byte[] duplicate = bar(REACTIONS, COMMENTS, FALLBACK, FALLBACK);
        assertSame(duplicate, move(duplicate));
    }

    @Test public void ignoresUnrelatedToastAndMalformedPayload() {
        byte[] otherAction = button(join(label("Download"), TOAST));
        byte[] bar = bar(REACTIONS, SHARE, otherAction);
        assertSame(bar, move(bar));
        byte[] malformed = join(bar(REACTIONS, COMMENTS, FALLBACK), new byte[] {(byte) 0xff});
        assertSame(malformed, move(malformed));
    }

    @Test public void acceptsChineseButtonWithEnglishHostResources() {
        byte[] fallback = button(join(label("歌词"), TOAST));
        assertArrayEquals(bar(REACTIONS, fallback, COMMENTS), move(bar(REACTIONS, COMMENTS, fallback)));
    }

    private static byte[] move(byte[] bar) { return LyricsActionOrder.moveFallback(bar, "Lyrics"); }
    private static byte[] label(String text) { return field(2, field(1, text.getBytes(StandardCharsets.UTF_8))); }
    private static byte[] bar(byte[]... buttons) {
        return field(1, field(12345, field(2, field(389155117, join(buttons)))));
    }
    private static byte[] button(byte[] payload) { return field(4, field(10, field(461054335, payload))); }
    private static byte[] field(int id, byte[] payload) {
        return join(varint(((long) id << 3) | 2), varint(payload.length), payload);
    }
    private static byte[] number(int id, int value) { return join(varint((long) id << 3), varint(value)); }
    private static byte[] varint(long value) {
        var out = new ByteArrayOutputStream();
        while (value > 127) {
            out.write(((int) value & 127) | 128);
            value >>>= 7;
        }
        out.write((int) value);
        return out.toByteArray();
    }
    private static byte[] join(byte[]... chunks) {
        var out = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) out.writeBytes(chunk);
        return out.toByteArray();
    }
}
