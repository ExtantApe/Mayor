package com.extantape.mayor.rules;

import java.util.Locale;
import java.util.UUID;

/**
 * The decision for D291: does this viewer get to see the author on a written book? The item work (swapping the author
 * line in and out) is in BookListener; this class only answers yes or no and masks a title, so it can be tested alone.
 */
public final class BookAuthors {
    private BookAuthors() {
    }

    /** Whether the author line may be shown to {@code viewer} (null means a place nobody is looking from, such as a chest nobody has open). */
    public static boolean mayShow(boolean hide, Reach reach, Subject viewer, String authorName, UUID taggedId,
                                  boolean authorIsPlayer, Subject author) {
        if (!hide || reach == null || !reach.active()) return true;
        if (viewer == null) return false;
        if (isSelf(viewer, authorName, taggedId)) return true;
        if (!authorIsPlayer) return true;
        return reach.mayKnowName(viewer, author);
    }

    /**
     * While Mayor's config can't be read there are no circles to follow, so only the author sees their name (D291).
     */
    public static boolean mayShowPaused(Subject viewer, String authorName, UUID taggedId) {
        return viewer != null && isSelf(viewer, authorName, taggedId);
    }

    private static boolean isSelf(Subject viewer, String authorName, UUID taggedId) {
        if (taggedId != null) return viewer.id().equals(taggedId);
        return authorName != null && viewer.name() != null && viewer.name().equalsIgnoreCase(authorName.trim());
    }

    /** The title with the author's name replaced by {@link WordFilter#MASK}, in any letter case. Other text is left as it is. */
    public static String maskTitle(String title, String authorName) {
        if (title == null || authorName == null || authorName.isBlank()) return title;
        String name = authorName.trim();
        StringBuilder out = new StringBuilder(title.length());
        String lower = title.toLowerCase(Locale.ROOT), want = name.toLowerCase(Locale.ROOT);
        int at = 0;
        for (int hit = lower.indexOf(want); hit >= 0; hit = lower.indexOf(want, at)) {
            out.append(title, at, hit).append(WordFilter.MASK);
            at = hit + want.length();
        }
        return out.append(title.substring(at)).toString();
    }
}
