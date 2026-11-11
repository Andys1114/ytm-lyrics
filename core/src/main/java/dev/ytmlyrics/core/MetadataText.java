package dev.ytmlyrics.core;

import java.util.regex.Pattern;

/** Presentation text added by YTM, separate from the underlying song metadata. */
final class MetadataText {
    private static final String SPACE = "[\\p{Zs}\\t]";
    private static final String CREDIT = SPACE + "*合作音[乐樂]人" + SPACE + "*[：:]";
    private static final Pattern COLLABORATORS = Pattern.compile(
            "（" + CREDIT + "[^（）\\r\\n]*）|\\(" + CREDIT + "[^()\\r\\n]*\\)");
    private static final Pattern SPACES = Pattern.compile(SPACE + "+");

    private MetadataText() {}

    static String withoutCollaborators(String value) {
        if (value == null) return "";
        var tags = COLLABORATORS.matcher(value);
        return tags.find() ? SPACES.matcher(tags.replaceAll(" ")).replaceAll(" ").strip() : value;
    }
}
