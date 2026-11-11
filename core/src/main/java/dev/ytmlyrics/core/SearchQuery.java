package dev.ytmlyrics.core;

import java.util.regex.Pattern;

/** Source search fields only; playback identity and returned lyrics retain their original text. */
public record SearchQuery(String title, String artist, String album, String keywords) {
    private static final Pattern SEPARATORS=Pattern.compile("[、和\\p{Z}\\s]+");

    public static SearchQuery of(Song song, String manualQuery) {
        String title=metadata(song.title());
        String artist=song.isVideo() ? "" : metadata(song.artist());
        String album=metadata(song.album());
        String keywords=manualQuery.isBlank() ? (title+" "+artist).strip() : ChineseText.simplify(manualQuery);
        return new SearchQuery(title,artist,album,keywords);
    }

    private static String metadata(String value) {
        String simplified=ChineseText.simplify(MetadataText.withoutCollaborators(value));
        return simplified.indexOf('、')<0 && simplified.indexOf('和')<0 ? simplified
                : SEPARATORS.matcher(simplified).replaceAll(" ").strip();
    }
}
