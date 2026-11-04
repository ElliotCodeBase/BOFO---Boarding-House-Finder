package com.bofo.finder.ui;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.model.AnnounceType;

import java.util.List;

/**
 * v2.4.7: the announcement type as a searchable dropdown. An anchored {@link PopupWindow} under the type field with a search box pinned at the top
 * (focused, keyboard opens, clear button) and the scrollable list of types ({@link AnnounceTypeAdapter}, filtered live by
 * {@code AnnounceFormState.filter}), an empty state, and a check mark on the chosen type.
 * <p>The panel keeps itself inside the visible part of the window: its height follows the space between the field and the keyboard / screen
 * bottom (re-measured whenever the sheet is laid out again), so on a small phone with the keyboard open it stays fully visible and scrolls.
 * Nothing is stored; the host decides what a pick means. Call {@link #dismiss()} when the host view goes away (rotation) so no window leaks.</p>
 */
final class AnnounceTypeDropdown {
    interface Listener { void onPick(AnnounceType t); }

    private static final int MIN_DP = 140, MAX_DP = 380, GAP_DP = 4, MARGIN_DP = 8;

    private final Context ctx;
    private final View anchor;
    private final Listener listener;
    private final float dp;
    private AnnounceType selected;
    private PopupWindow popup;
    private AnnounceTypeAdapter adapter;
    private RecyclerView list;
    private TextView empty;
    private android.widget.EditText search;
    private int lastHeight = -1, lastWidth = -1;
    private final ViewTreeObserver.OnGlobalLayoutListener relayout = this::fit;
    private ViewTreeObserver.OnGlobalLayoutListener waiting;       // showWhenReady: the anchor is not laid out yet

    AnnounceTypeDropdown(@NonNull Context ctx, @NonNull View anchor, @Nullable Listener listener) {
        this.ctx = ctx; this.anchor = anchor; this.listener = listener;
        this.dp = ctx.getResources().getDisplayMetrics().density;
    }

    boolean isShowing() { return popup != null && popup.isShowing(); }

    /** The chosen type gets the check mark. */
    void setSelected(@Nullable AnnounceType t) {
        selected = t;
        if (adapter != null) adapter.setSelected(t);
    }

    /** Opens the panel once the anchor is laid out (right after the sheet appears it may still be 0 px wide). */
    void showWhenReady() {
        if (isShowing() || waiting != null) return;
        if (ready()) { show(); return; }
        waiting = () -> {
            if (!ready()) return;
            removeWaiting();
            show();
        };
        anchor.getViewTreeObserver().addOnGlobalLayoutListener(waiting);
    }

    private boolean ready() { return anchor.isAttachedToWindow() && anchor.getWidth() > 0 && anchor.getWindowToken() != null; }

    private void removeWaiting() {
        if (waiting == null) return;
        ViewTreeObserver o = anchor.getViewTreeObserver();
        if (o.isAlive()) o.removeOnGlobalLayoutListener(waiting);
        waiting = null;
    }

    void show() {
        if (isShowing() || !ready()) return;
        View content = LayoutInflater.from(ctx).inflate(R.layout.popup_announce_type, null, false);
        search = content.findViewById(R.id.ann_dd_search);
        empty = content.findViewById(R.id.ann_dd_empty);
        list = content.findViewById(R.id.ann_dd_list);
        adapter = new AnnounceTypeAdapter(ctx, this::picked);
        adapter.setSelected(selected);
        list.setLayoutManager(new LinearLayoutManager(ctx));
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { refilter(); }
        });
        search.setOnEditorActionListener((v, action, ev) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); return true; }
            return false;
        });
        refilter();

        lastWidth = anchor.getWidth();
        lastHeight = availableHeight();
        popup = new PopupWindow(content, lastWidth, lastHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(0));              // the panel draws its own rounded background
        popup.setElevation(8 * dp);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        popup.setOnDismissListener(() -> {
            ViewTreeObserver o = anchor.getViewTreeObserver();
            if (o.isAlive()) o.removeOnGlobalLayoutListener(relayout);
            hideKeyboard();
            popup = null; adapter = null; list = null; empty = null; search = null;
        });
        popup.showAsDropDown(anchor, 0, Math.round(GAP_DP * dp));
        anchor.getViewTreeObserver().addOnGlobalLayoutListener(relayout);
        int at = adapter.selectedIndex();
        if (at > 2) list.scrollToPosition(at);                         // the chosen row is in view when reopened
        search.requestFocus();
        search.post(() -> {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && search != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    void dismiss() {
        removeWaiting();
        if (popup != null) popup.dismiss();                              // the dismiss listener clears the rest
    }

    private void picked(AnnounceType t) {
        dismiss();
        if (listener != null) listener.onPick(t);
    }

    private void refilter() {
        if (adapter == null) return;
        List<AnnounceType> shown = AnnounceTypeAdapter.filter(ctx, search.getText() == null ? "" : search.getText().toString());
        adapter.submit(shown);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Space between the bottom of the field and the bottom of the visible window (above the keyboard), within [MIN, MAX] dp. */
    private int availableHeight() {
        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int below = frame.bottom - (at[1] + anchor.getHeight()) - Math.round((GAP_DP + MARGIN_DP) * dp);
        return Math.max(Math.round(MIN_DP * dp), Math.min(Math.round(MAX_DP * dp), below));
    }

    /** The sheet moved or resized (keyboard, rotation of the screen size): follow it. */
    private void fit() {
        if (!isShowing()) return;
        int h = availableHeight(), w = anchor.getWidth();
        if (h == lastHeight && w == lastWidth) return;
        lastHeight = h; lastWidth = w;
        popup.update(anchor, 0, Math.round(GAP_DP * dp), w, h);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && anchor.getWindowToken() != null) imm.hideSoftInputFromWindow(anchor.getWindowToken(), 0);
    }
}
