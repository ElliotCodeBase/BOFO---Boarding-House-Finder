package com.bofo.finder.util;

import static org.junit.Assert.*;

import com.bofo.finder.model.AnnounceType;

import java.util.List;

import org.junit.Test;

/** v2.4.7: type dropdown rules (plain JVM). */
public class AnnounceTypePickerRulesTest {
    private static AnnounceType t(String id) { return AnnounceType.byId(id); }

    @Test public void opensByItselfOnlyOnTheFirstOpenWithNoType() {
        assertTrue(AnnounceTypePickerRules.autoOpen(true, null));
        assertFalse("after a rotation", AnnounceTypePickerRules.autoOpen(false, null));
        assertFalse("a type is already chosen", AnnounceTypePickerRules.autoOpen(true, t("rent_due")));
    }

    @Test public void detailsOnlyAfterAType() {
        assertFalse(AnnounceTypePickerRules.showDetails(null));
        assertTrue(AnnounceTypePickerRules.showDetails(t("rent_due")));
    }

    @Test public void samePickKeepsDetailsOtherPickClearsThem() {
        assertTrue(AnnounceTypePickerRules.isFreshPick(null, t("rent_due")));
        assertFalse(AnnounceTypePickerRules.isFreshPick(t("rent_due"), t("rent_due")));
        assertTrue(AnnounceTypePickerRules.isFreshPick(t("rent_due"), AnnounceType.ALL.get(AnnounceType.ALL.size() - 1).id.equals("rent_due") ? AnnounceType.ALL.get(0) : AnnounceType.ALL.get(AnnounceType.ALL.size() - 1)));
        assertFalse(AnnounceTypePickerRules.isFreshPick(t("rent_due"), null));
    }

    @Test public void selectedRowGetsTheCheck() {
        assertTrue(AnnounceTypePickerRules.isSelected(t("rent_due"), t("rent_due")));
        assertFalse(AnnounceTypePickerRules.isSelected(null, t("rent_due")));
    }

    @Test public void waterSearchFindsTheWaterType() {                    // the live filter is the existing one (no new filter logic)
        List<AnnounceType> hit = AnnounceFormState.filter(AnnounceType.ALL, "water", t -> t.id.replace('_', ' '));
        assertFalse(hit.isEmpty());
        for (AnnounceType x : hit) assertTrue(x.id.contains("water"));
    }
}
