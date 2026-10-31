package com.bofo.finder.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentHomeBinding;
import com.bofo.finder.databinding.ItemCampusOptionBinding;
import com.bofo.finder.model.College;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.CampusMatch;
import com.bofo.finder.util.ListRules;
import com.bofo.finder.util.Regions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The ONE Home search bar (original: CampusPicker in HomeView, "Search campus or place"). Type to filter the campuses, tap the arrow for the whole list,
 * pick one = BrowseState.setCampus. Enter on text that is not a campus opens Explore with it. X clears the text (and the campus, if the text was the campus).
 * The dropdown floats over the page under the pill, same colleges source as before (BrowseState.colleges, nearest first).
 */
final class HomeSearchBar {

    interface Host {
        /** "Near me" row: ask for one position and measure from the phone. */
        void nearMe();
        /** Enter on text that matches no campus: search for it in Explore. */
        void searchPlace(String text);
        /** Step 5: the text was typed in or cleared (the map's overview mode depends on the search being empty). */
        default void searchChanged() { }
    }

    private static final int ROW_DP = 64, MAX_ROWS = 5;

    private final Fragment fragment;
    private final FragmentHomeBinding b;
    private final Host host;
    private final float dp;
    private final Adapter adapter = new Adapter();
    private final OnBackPressedCallback backClose;
    private boolean settingText, open, synced;
    private String syncedCampusId;
    private boolean syncedNear;

    HomeSearchBar(Fragment fragment, FragmentHomeBinding binding, Host host) {
        this.fragment = fragment; this.b = binding; this.host = host;
        dp = fragment.getResources().getDisplayMetrics().density;
        b.dropdownList.setLayoutManager(new LinearLayoutManager(fragment.requireContext()));
        b.dropdownList.setAdapter(adapter);
        b.dropdownList.setItemAnimator(null);

        backClose = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { dismiss(true); }
        };
        fragment.requireActivity().getOnBackPressedDispatcher().addCallback(fragment.getViewLifecycleOwner(), backClose);

        b.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                if (settingText) return;
                paintClear();
                showFor(s.toString(), true);
                host.searchChanged();
            }
        });
        b.searchInput.setOnFocusChangeListener((v, has) -> {
            b.searchPill.setActivated(has);
            if (has) { if (isChosenText()) b.searchInput.selectAll(); showFor(isChosenText() ? "" : text(), true); }
        });
        b.searchInput.setOnClickListener(v -> { if (!open) showFor(isChosenText() ? "" : text(), true); });
        b.searchInput.setOnEditorActionListener((v, action, ev) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) { enter(); return true; }
            return false;
        });
        b.searchClear.setOnClickListener(v -> clear());
        b.searchArrow.setOnClickListener(v -> {
            if (open) { dismiss(true); return; }
            if (!b.searchInput.hasFocus()) b.searchInput.requestFocus();
            showFor("", true);
        });
        b.dropdownScrim.setOnClickListener(v -> dismiss(true));
    }

    /** Makes the field show what the app state says (chosen campus / Near me / empty). Only acts when that state changed, so typed text is never wiped. */
    void syncFromState() {
        College c = BrowseState.campus();
        String id = c == null ? null : c.id();
        boolean near = c == null && BrowseState.nearMe;
        if (synced && Objects.equals(id, syncedCampusId) && near == syncedNear) return;
        synced = true; syncedCampusId = id; syncedNear = near;
        setText(c != null ? c.name() : near ? ctx().getString(R.string.home_near_me_row) : "");
    }

    boolean isOpen() { return open; }

    /** Step 7: the rent card's "Choose": focus the field and show the campus list. */
    void openDropdown() { b.searchInput.requestFocus(); showFor("", true); }

    /** Step 5: nothing typed (a chosen campus / Near me counts as text). */
    boolean isEmpty() { return text().trim().isEmpty(); }

    /** The header scrolls with the list (Step 4): an open dropdown would be left behind, so it closes. */
    void closeOnScroll() { if (open) dismiss(true); }

    // ------------------------------------------------------------------ actions

    private void enter() {
        String t = text().trim();
        if (t.isEmpty() || isChosenText()) { dismiss(true); return; }
        College first = open ? adapter.firstCollege() : null;
        if (first != null) { pick(first); return; }              // Enter = best match
        dismiss(true);
        host.searchPlace(t);                                     // not a campus: look for it in Explore
    }

    private void clear() {
        boolean wasCampus = isChosenText();
        boolean wasNear = BrowseState.nearMe && text().equals(ctx().getString(R.string.home_near_me_row));
        setText("");
        if (wasCampus) { syncedCampusId = null; BrowseState.setCampus(null); }      // X on the campus name clears the campus; on typed text only the text
        else if (wasNear) { syncedNear = false; BrowseState.clearNearMe(); }
        b.searchInput.requestFocus();
        showFor("", true);
        host.searchChanged();
    }

    private void pick(College c) {
        dismiss(true);
        setText(c.name());
        synced = true; syncedCampusId = c.id(); syncedNear = false;
        BrowseState.setCampus(c);                                // saves default_college_id and reloads Home
    }

    // ------------------------------------------------------------------ dropdown

    private void showFor(String typed, boolean canLoad) {
        if (b.getRoot() == null) return;
        List<College> all = BrowseState.colleges;
        if (all.isEmpty() && canLoad) {                          // not loaded yet: load once, then show
            BrowseState.ensureLoaded(() -> { if (b.searchInput.hasFocus() || open) showFor(text(), false); }, e -> { });
            return;
        }
        String q = typed == null ? "" : typed.trim();
        List<College> list = q.isEmpty() ? ordered(all) : CampusMatch.match(all, q);
        if (list.isEmpty() && !q.isEmpty()) { closeList(); return; }     // typed text is a place name, not a campus: no empty dropdown
        adapter.set(list, q.isEmpty(), q);
        int rows = Math.min(Math.max(adapter.getItemCount(), 1), MAX_ROWS);
        ViewGroup.LayoutParams lp = b.dropdownList.getLayoutParams();
        lp.height = Math.round(rows * ROW_DP * dp);
        b.dropdownList.setLayoutParams(lp);
        int[] pill = new int[2], root = new int[2];
        b.searchPill.getLocationOnScreen(pill);
        b.getRoot().getLocationOnScreen(root);
        b.campusDropdown.setTranslationY(pill[1] - root[1] + b.searchPill.getHeight() + 6 * dp);
        b.campusDropdown.setVisibility(View.VISIBLE);
        b.dropdownScrim.setVisibility(View.VISIBLE);
        open = true;
        backClose.setEnabled(true);
    }

    private void closeList() {
        open = false;
        b.campusDropdown.setVisibility(View.GONE);
        b.dropdownScrim.setVisibility(View.GONE);
        backClose.setEnabled(false);
    }

    private void dismiss(boolean dropFocus) {
        closeList();
        if (!dropFocus) return;
        InputMethodManager imm = (InputMethodManager) ctx().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.searchInput.getWindowToken(), 0);
        b.searchInput.clearFocus();                              // the Home root takes the focus (focusableInTouchMode)
    }

    /** Nearest campus first: from the chosen campus, else the phone (Near me), else the middle of the user's region, else A-Z. */
    private List<College> ordered(List<College> all) {
        Double lat = BrowseState.refLat(), lng = BrowseState.refLng();
        if (lat == null && BrowseState.me != null) {
            Regions.Region r = Regions.byKey(BrowseState.me.region());
            if (r != null) { lat = r.lat(); lng = r.lng(); }
        }
        return ListRules.nearestFirst(all, lat, lng);
    }

    // ------------------------------------------------------------------ small helpers

    private Context ctx() { return fragment.requireContext(); }
    private String text() { return b.searchInput.getText().toString(); }

    private boolean isChosenText() {
        College c = BrowseState.campus();
        return c != null && text().trim().equalsIgnoreCase(c.name());
    }

    private void setText(String t) {
        settingText = true;
        b.searchInput.setText(t);
        settingText = false;
        paintClear();
    }

    private void paintClear() { b.searchClear.setVisibility(b.searchInput.length() > 0 ? View.VISIBLE : View.GONE); }

    /** Campus name with the part the user typed in bold. */
    private static CharSequence highlight(String name, String typed) {
        String q = typed == null ? "" : typed.trim();
        int i = q.isEmpty() ? -1 : name.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));
        if (i < 0 || i + q.length() > name.length()) return name;
        SpannableString sp = new SpannableString(name);
        sp.setSpan(new StyleSpan(Typeface.BOLD), i, i + q.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    // ------------------------------------------------------------------ rows

    private final class Adapter extends RecyclerView.Adapter<Adapter.Row> {
        private List<College> items = new ArrayList<>();
        private boolean nearRow;
        private String typed = "";

        void set(List<College> list, boolean withNearMe, String q) {
            items = list; nearRow = withNearMe; typed = q;
            notifyDataSetChanged();
        }

        College firstCollege() { return items.isEmpty() ? null : items.get(0); }

        @Override public int getItemCount() { return items.size() + (nearRow ? 1 : 0); }

        @NonNull @Override
        public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Row(ItemCampusOptionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Row h, int pos) {
            if (nearRow && pos == 0) {
                h.v.icon.setImageResource(R.drawable.ic_my_location);
                ImageViewCompat.setImageTintList(h.v.icon, android.content.res.ColorStateList.valueOf(Color.parseColor("#2F6FED")));
                h.v.title.setText(R.string.home_near_me_row);
                h.v.sub.setText(R.string.home_near_me_sub);
                h.v.sub.setVisibility(View.VISIBLE);
                h.v.getRoot().setOnClickListener(x -> { dismiss(true); host.nearMe(); });
                return;
            }
            final College c = items.get(pos - (nearRow ? 1 : 0));
            String[] parts = CampusMatch.split(c.name());
            h.v.icon.setImageResource(R.drawable.ic_campus_dot);
            ImageViewCompat.setImageTintList(h.v.icon, null);
            h.v.title.setText(highlight(parts[0], typed));
            h.v.sub.setText(parts[1]);
            h.v.sub.setVisibility(parts[1].isEmpty() ? View.GONE : View.VISIBLE);
            h.v.getRoot().setOnClickListener(x -> pick(c));
        }

        final class Row extends RecyclerView.ViewHolder {
            final ItemCampusOptionBinding v;
            Row(ItemCampusOptionBinding v) { super(v.getRoot()); this.v = v; }
        }
    }
}
