package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class Resolver {
    public record Result(Candidate selected, List<Candidate> candidates, List<String> errors, boolean cancelled) {}
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
                List<Candidate> found = source.search(song, "");
                if (cancelled.getAsBoolean()) return new Result(null, List.of(), List.of(), true);
                candidates.addAll(found);
                for (Candidate candidate : found) {
                    if (!Matching.reliable(song,candidate)) continue;
                    if (best == null || Matching.compare(song,candidate,best) > 0) {
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
        return new Result(best, List.copyOf(candidates), List.copyOf(errors), false);
    }
}
