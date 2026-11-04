package com.bofo.finder.util;

import com.bofo.finder.model.AnnounceType;

/**
 * v2.4.7: the rules of the announcement-type dropdown in the "Write for me" sheet (plain JVM, unit tested). The sheet opens straight on the
 * details form; the type is chosen from a searchable dropdown ({@link AnnounceFormState#filter} does the matching).
 */
public final class AnnounceTypePickerRules {
    private AnnounceTypePickerRules() { }

    /** The dropdown opens by itself once, on the very first open of the sheet with no type chosen; never again after a rotation. */
    public static boolean autoOpen(boolean firstCreate, AnnounceType chosen) { return firstCreate && chosen == null; }

    /** The details form (fields, options, Write for me) only shows once a type is chosen. */
    public static boolean showDetails(AnnounceType chosen) { return chosen != null; }

    /** Picking the already chosen type again changes nothing (the typed details are kept); anything else is a fresh pick that clears them. */
    public static boolean isFreshPick(AnnounceType current, AnnounceType picked) {
        return picked != null && (current == null || !current.id.equals(picked.id));
    }

    /** The row of the chosen type shows a check mark. */
    public static boolean isSelected(AnnounceType chosen, AnnounceType row) { return chosen != null && row != null && chosen.id.equals(row.id); }
}
