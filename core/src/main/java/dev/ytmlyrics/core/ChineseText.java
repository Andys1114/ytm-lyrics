package dev.ytmlyrics.core;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

/** Offline normalization for search and comparison, never for persisted identity or lyric text. */
public final class ChineseText {
    private ChineseText() {}
    public static String simplify(String value) { return value == null ? "" : ZhConverterUtil.toSimple(value); }
}
