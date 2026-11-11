package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class Resolver {
    public record Result(Candidate selected, List<Candidate> candidates, List<String> errors, boolean cancelled) {}

    /** Reuse the same playback selection for a cached pool and newly fetched candidates. */
    public static Candidate select(Song song, List<Candidate> candidates) {
        Candidate best = null;
        for (Candidate candidate : candidates) if (better(song, candidate, best)) best = candidate;
        return finish(song, best, candidates);
    }

    private static boolean better(Song song, Candidate candidate, Candidate best) {
        return LyricsResults.displayable(candidate) && Matching.reliable(song, candidate)
                && (best == null || Matching.compare(song, candidate, best) > 0);
    }

    private static Candidate finish(Song song, Candidate direct, List<Candidate> candidates) {
        Candidate chosen = direct != null ? direct : RecordingFallback.select(song, candidates);
        return TranslationSupplement.apply(song, chosen, candidates);
    }

    public Result resolve(Song song, List<LyricsSource> sources, BooleanSupplier cancelled) {
        return resolve(song,sources,cancelled,candidate -> {});
    }
    public Result resolve(Song song, List<LyricsSource> sources, BooleanSupplier cancelled, Consumer<Candidate> provisional) {
        List<Candidate> candidates = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Candidate best = null;
        for (LyricsSource source : sources) {
            if (cancelled.getAsBoolean()) return new Result(null, List.of(), List.of(), true);
            try {
                List<Candidate> found = LyricsResults.timed(source.search(song, ""));
                if (cancelled.getAsBoolean()) return new Result(null, List.of(), List.of(), true);
                candidates.addAll(found);
                for (Candidate candidate : found) {
                    if (better(song, candidate, best)) {
                        best = candidate;
                        if (best.hasSynced()) provisional.accept(best);
                    }
                }
            } catch (Exception error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return new Result(null, List.of(), List.of(), true);
                }
                errors.add(source.name() + "：" + error.getMessage());
            }
        }
        if (cancelled.getAsBoolean()) return new Result(null, List.of(), List.of(), true);
        return new Result(finish(song, best, candidates), List.copyOf(candidates), List.copyOf(errors), false);
    }
}
