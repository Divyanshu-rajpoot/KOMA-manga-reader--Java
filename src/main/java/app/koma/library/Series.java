package app.koma.library;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** A series groups volumes. Public fields map 1:1 to library.json. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Series {
    public String id;
    public String title;
    public String titleJp;
    /** "manga" or "manhwa". */
    public String type = "manga";
    /** "rtl", "ltr" or "ttb" (long strip). */
    public String direction = "rtl";
    public String addedAt;
    public List<Volume> volumes = new ArrayList<>();

    @JsonIgnore
    public List<Volume> sortedVolumes() {
        List<Volume> v = new ArrayList<>(volumes);
        v.sort(Comparator.comparingDouble((Volume x) -> x.number).thenComparing(Volume::fileName));
        return v;
    }

    @JsonIgnore
    public long totalSize() {
        return volumes.stream().mapToLong(v -> v.size).sum();
    }

    @JsonIgnore
    public int totalPages() {
        return volumes.stream().mapToInt(v -> v.pages).sum();
    }

    @JsonIgnore
    public long finishedCount() {
        return volumes.stream().filter(Volume::isFinished).count();
    }

    /** Most recently read volume, if any. */
    @JsonIgnore
    public Optional<Volume> lastRead() {
        return volumes.stream()
                .filter(v -> v.progress != null && v.progress.updatedAt != null)
                .max(Comparator.comparing(v -> v.progress.updatedAt));
    }

    @JsonIgnore
    public String lastReadAt() {
        return lastRead().map(v -> v.progress.updatedAt).orElse(null);
    }

    /** The volume to resume: last read if unfinished, else the first unfinished after it, else the first. */
    @JsonIgnore
    public Volume current() {
        List<Volume> sorted = sortedVolumes();
        if (sorted.isEmpty()) return null;
        Optional<Volume> last = lastRead();
        if (last.isPresent()) {
            Volume l = last.get();
            if (!l.isFinished()) return l;
            int i = sorted.indexOf(l);
            for (int k = i + 1; k < sorted.size(); k++) if (!sorted.get(k).isFinished()) return sorted.get(k);
            return l;
        }
        return sorted.stream().filter(v -> !v.isFinished()).findFirst().orElse(sorted.get(0));
    }

    /** "reading", "finished" or "unread". */
    @JsonIgnore
    public String status() {
        if (!volumes.isEmpty() && finishedCount() == volumes.size()) return "finished";
        if (volumes.stream().anyMatch(Volume::isStarted)) return "reading";
        return "unread";
    }

    @JsonIgnore
    public String directionLabel() {
        return switch (direction) {
            case "ltr" -> "LEFT-TO-RIGHT";
            case "ttb" -> "LONG STRIP";
            default -> "RIGHT-TO-LEFT";
        };
    }
}
