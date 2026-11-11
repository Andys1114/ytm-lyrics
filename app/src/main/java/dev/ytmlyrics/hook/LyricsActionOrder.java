package dev.ytmlyrics.hook;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Moves the native no-lyrics action before Litho lays out the player action bar. */
final class LyricsActionOrder {
    // YTM 9.41.52 Elements extensions. The client filler puts button data in
    // VideoActionBarModel.buttons (4), with each button's view model in field 10.
    private static final int BAR = 389155117;
    private static final int REACTIONS = 484586709;
    private static final int BUTTON = 461054335;
    private static final int FALLBACK_TOAST = 76623563;
    private static final int MAX_DEPTH = 10;
    private static final byte[] CHINESE_TITLE = "歌词".getBytes(StandardCharsets.UTF_8);

    /** Returns the original array if this is not a recognized bar or no move is needed. */
    static byte[] moveFallback(byte[] element, String localizedTitle) {
        return edit(element, element, 0, element.length, 0, localizedTitle.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] edit(byte[] source, byte[] output, int start, int end, int depth, byte[] title) {
        if (depth == MAX_DEPTH) return output;
        for (Field field : fields(source, start, end)) {
            if (!field.message()) continue;
            if (field.number() == BAR) {
                List<Field> model = fields(source, field.payload(), field.end());
                List<Field> reordered = reorder(source, model, title);
                if (reordered == model) continue;
                if (output == source) output = source.clone();
                int offset = field.payload();
                for (Field entry : reordered) {
                    int size = entry.end() - entry.start();
                    System.arraycopy(source, entry.start(), output, offset, size);
                    offset += size;
                }
            } else {
                output = edit(source, output, field.payload(), field.end(), depth + 1, title);
            }
        }
        return output;
    }

    private static List<Field> reorder(byte[] bytes, List<Field> model, byte[] title) {
        var buttons = new ArrayList<Field>();
        Field reactions = null, lyrics = null;
        for (Field entry : model) {
            if (entry.number() != 4 || !entry.message()) continue;
            buttons.add(entry);
            for (Field viewModel : fields(bytes, entry.payload(), entry.end())) {
                if (viewModel.number() != 10 || !viewModel.message()) continue;
                for (Field extension : fields(bytes, viewModel.payload(), viewModel.end())) {
                    if (!extension.message()) continue;
                    if (extension.number() == REACTIONS) {
                        if (reactions != null) return model;
                        reactions = entry;
                    } else if (extension.number() == BUTTON && isFallbackLyrics(bytes, extension, title)) {
                        if (lyrics != null) return model;
                        lyrics = entry;
                    }
                }
            }
        }
        if (reactions == null || lyrics == null || reactions == lyrics
                || buttons.indexOf(lyrics) == buttons.indexOf(reactions) + 1) return model;

        buttons.remove(lyrics);
        buttons.add(buttons.indexOf(reactions) + 1, lyrics);
        var ordered = new ArrayList<>(model);
        int button = 0;
        for (int i = 0; i < model.size(); i++) {
            Field entry = model.get(i);
            if (entry.number() == 4 && entry.message()) ordered.set(i, buttons.get(button++));
        }
        return ordered;
    }

    private static boolean isFallbackLyrics(byte[] bytes, Field button, byte[] title) {
        return (lyricMarkers(bytes, button, title, 0) & 3) == 3;
    }

    private static int lyricMarkers(byte[] bytes, Field message, byte[] title, int depth) {
        if (depth == MAX_DEPTH) return 0;
        int markers = 0;
        for (Field field : fields(bytes, message.payload(), message.end())) {
            if (!field.message()) continue;
            if (field.number() == FALLBACK_TOAST) markers |= 1;
            else if (matches(bytes, field, title) || matches(bytes, field, CHINESE_TITLE)) markers |= 2;
            else markers |= lyricMarkers(bytes, field, title, depth + 1);
            if (markers == 3) break;
        }
        return markers;
    }

    private static boolean matches(byte[] bytes, Field field, byte[] title) {
        return title.length != 0 && Arrays.equals(bytes, field.payload(), field.end(), title, 0, title.length);
    }

    // Keep the original encoded fields (including unknown ones) and move whole button
    // records so the host's key, label, and click command stay together.
    private record Field(int number, int wire, int start, int payload, int end) {
        boolean message() { return wire == 2; }
    }

    private static List<Field> fields(byte[] bytes, int start, int end) {
        var fields = new ArrayList<Field>();
        int[] cursor = {start};
        while (cursor[0] < end) {
            int fieldStart = cursor[0];
            long key = varint(bytes, cursor, end);
            if (key < 8 || key > 0xffffffffL) return List.of();
            int wire = (int) key & 7;
            long size;
            switch (wire) {
                case 0 -> {
                    varint(bytes, cursor, end);
                    if (cursor[0] > end) return List.of();
                    size = 0;
                }
                case 1 -> size = 8;
                case 2 -> size = varint(bytes, cursor, end);
                case 5 -> size = 4;
                default -> { return List.of(); }
            }
            int payload = cursor[0];
            if (size < 0 || size > end - payload) return List.of();
            cursor[0] += (int) size;
            fields.add(new Field((int) (key >>> 3), wire, fieldStart, payload, cursor[0]));
        }
        return fields;
    }

    private static long varint(byte[] bytes, int[] cursor, int end) {
        long value = 0;
        for (int shift = 0; shift < 64 && cursor[0] < end; shift += 7) {
            int b = bytes[cursor[0]++] & 0xff;
            if (shift == 63 && b > 1) break;
            value |= (long) (b & 0x7f) << shift;
            if (b < 128) return value;
        }
        cursor[0] = end + 1;
        return -1;
    }
}
