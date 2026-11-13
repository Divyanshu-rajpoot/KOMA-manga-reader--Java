package app.koma.library;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One CBZ file. Public fields map 1:1 to library.json. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Volume {
    public String id = UUID.randomUUID().toString();
    /** Volume or chapter number (may be fractional for chapters like 12.5). */
    public double number;
    /** Last volume of an omnibus range ("01-05"), else null. */
    public Double numberEnd;
    /** "volume", "chapter" or "omnibus". */
    public String kind = "volume";
    public String path;
    /** "linked" or "copied". */
    public String storage = "linked";
    public long size;
    public long mtime;
    public int pages;
    public String addedAt;
    public Progress progress;
    public List<Session> sessions = new ArrayList<>();

    public static class Progress {
        public int page;
        public String updatedAt;
        public boolean finished;

        public Progress() {}

        public Progress(int page, String updatedAt, boolean finished) {
            this.page = page;
            this.updatedAt = updatedAt;
            this.finished = finished;
        }
    }

    public static class Session {
        public String date;
        public int from;
        public int to;

        public Session() {}

        public Session(String date, int from, int to) {
            this.date = date;
            this.from = from;
            this.to = to;
        }
    }

    @JsonIgnore
    public String fileName() {
        return path == null ? "" : Path.of(path).getFileName().toString();
    }

    @JsonIgnore
    public boolean isFinished() {
        return progress != null && progress.finished;
    }

    @JsonIgnore
    public boolean isStarted() {
        return progress != null && (progress.page > 0 || progress.finished);
    }

    /** 0..1 read fraction. */
    @JsonIgnore
    public double fraction() {
        if (progress == null || pages <= 0) return 0;
        if (progress.finished) return 1;
        return Math.min(1, (progress.page + 1) / (double) pages);
    }

    /** "07", "12.5", "01-05". */
    @JsonIgnore
    public String label() {
        String n = num(number);
        if (numberEnd != null) return n + "–" + num(numberEnd);
        return n;
    }

    public static String num(double d) {
        if (d == Math.rint(d)) {
            long l = (long) d;
            return l < 10 && l >= 0 ? "0" + l : Long.toString(l);
        }
        return Double.toString(d);
    }
}
