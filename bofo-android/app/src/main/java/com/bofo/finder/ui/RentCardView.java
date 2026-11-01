package com.bofo.finder.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bofo.finder.R;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.util.Money;
import com.bofo.finder.util.PriceRange;

import java.util.ArrayList;
import java.util.List;

/**
 * Home's rent card (Step 7). With a campus: Lowest / Middle (or Best rated) / Highest values over a thin line with one marker per price at its real
 * position; tap a value or a marker and that house glows on the map (the value turns red; tap again = off). Without a campus: one line + "Choose".
 */
public class RentCardView extends View {

    public interface Listener {
        /** houseId = the house to light up on the map, null = none. */
        void glow(@Nullable String houseId);
        /** "Choose" was tapped: open the campus dropdown. */
        void choose();
    }

    private static final int GLOW = 0xFFF43F5E;
    private record Cell(String label, String id, double price) { }
    private record Mark(String id, double price) { }

    private final List<Cell> cells = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    private final List<RectF> cellHit = new ArrayList<>();
    private final RectF chooseHit = new RectF();
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double min, max;
    private boolean chooseMode;
    private String glowId;
    private Listener listener;

    public RentCardView(Context c) { this(c, null); }

    public RentCardView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
    }

    public void setListener(Listener l) { listener = l; }

    /** No campus chosen: the one-line version. */
    public void setNoCampus() {
        chooseMode = true; cells.clear(); marks.clear(); glowId = null;
        setVisibility(VISIBLE);
        requestLayout(); invalidate();
    }

    /** basis = the houses whose prices are shown. best = the Best rated tab (the middle column becomes the top rated house). Null / empty hides the card. */
    public void setData(@Nullable List<BoardingHouse> basis, boolean best) {
        chooseMode = false; cells.clear(); marks.clear(); glowId = null;
        List<BoardingHouse> h = new ArrayList<>();
        if (basis != null) for (BoardingHouse x : basis) if (x.price() > 0) h.add(x);
        List<Double> prices = new ArrayList<>();
        for (BoardingHouse x : h) prices.add(x.price());
        PriceRange r = PriceRange.of(prices);
        if (r == null) { setVisibility(GONE); return; }
        for (BoardingHouse x : h) marks.add(new Mark(x.id(), x.price()));
        min = r.lowest; max = r.highest;
        BoardingHouse lo = h.get(0), hi = h.get(0), mid = h.get(0), top = null;
        for (BoardingHouse x : h) {
            if (x.price() < lo.price()) lo = x;
            if (x.price() > hi.price()) hi = x;
            if (Math.abs(x.price() - r.middle) < Math.abs(mid.price() - r.middle)) mid = x;
            if (x.reviewCount() > 0 && (top == null || x.avgRating() > top.avgRating() || (x.avgRating() == top.avgRating() && x.price() < top.price()))) top = x;
        }
        Context c = getContext();
        if (best && top != null) {                                              // v2.0.105: Best rated tab = only the best rated price (no Lowest / Highest, one marker)
            marks.clear(); marks.add(new Mark(top.id(), top.price()));
            min = max = top.price();
            cells.add(new Cell(c.getString(R.string.rent_best), top.id(), top.price()));
            setVisibility(VISIBLE);
            requestLayout(); invalidate();
            return;
        }
        if (r.count == 1 || r.lowest == r.highest) cells.add(new Cell(c.getString(R.string.rent_single), lo.id(), lo.price()));
        else {
            cells.add(new Cell(c.getString(R.string.rent_lowest), lo.id(), lo.price()));
            if (r.count >= 3) {
                if (best && top != null) cells.add(new Cell(c.getString(R.string.rent_best), top.id(), top.price()));
                else cells.add(new Cell(c.getString(R.string.rent_middle), mid.id(), mid.price()));
            }
            cells.add(new Cell(c.getString(R.string.rent_highest), hi.id(), hi.price()));
        }
        setVisibility(VISIBLE);
        requestLayout(); invalidate();
    }

    @Override
    protected void onMeasure(int w, int hs) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round((chooseMode ? 54 : 92) * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        c.drawRoundRect(new RectF(0, 0, w, h), 18 * d, 18 * d, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d); p.setColor(0xFFECE8FA);
        c.drawRoundRect(new RectF(d / 2, d / 2, w - d / 2, h - d / 2), 18 * d, 18 * d, p);
        int text2 = ContextCompat.getColor(getContext(), R.color.bofo_text_2), ink = ContextCompat.getColor(getContext(), R.color.bofo_ink);
        int primary = ContextCompat.getColor(getContext(), R.color.bofo_primary);
        float pad = 16 * d;
        p.setStyle(Paint.Style.FILL);
        if (chooseMode) {
            p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.DEFAULT); p.setTextSize(13 * sd()); p.setColor(text2);
            float y = h / 2f - (p.ascent() + p.descent()) / 2f;
            c.drawText(getContext().getString(R.string.rent_choose_line), pad, y, p);
            String go = getContext().getString(R.string.rent_choose);
            p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(primary); p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(go, w - pad, y, p);
            float gw = p.measureText(go);
            chooseHit.set(w - pad - gw - 14 * d, 0, w, h);
            return;
        }
        cellHit.clear();
        String mo = getContext().getString(R.string.rent_per_month);
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            Paint.Align al = cells.size() == 1 || i == 0 ? Paint.Align.LEFT : i == cells.size() - 1 ? Paint.Align.RIGHT : Paint.Align.CENTER;
            float x = al == Paint.Align.LEFT ? pad : al == Paint.Align.RIGHT ? w - pad : w / 2f;
            boolean on = cell.id().equals(glowId);
            p.setTextAlign(al); p.setTypeface(Typeface.DEFAULT); p.setTextSize(11 * sd()); p.setColor(text2);
            c.drawText(cell.label(), x, 22 * d, p);
            String val = Money.peso(cell.price());
            p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(18 * sd()); p.setColor(on ? GLOW : ink);
            float vw = p.measureText(val);
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(11 * sd());
            float mw = p.measureText(mo);
            p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(18 * sd());
            // value + small "/mo" as one unit, aligned like the label
            float total = vw + 2 * d + mw, left = al == Paint.Align.LEFT ? x : al == Paint.Align.RIGHT ? x - total : x - total / 2f;
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText(val, left, 46 * d, p);
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(11 * sd()); p.setColor(on ? GLOW : text2);
            c.drawText(mo, left + vw + 2 * d, 46 * d, p);
            cellHit.add(new RectF(left - 8 * d, 6 * d, left + total + 8 * d, 54 * d));
        }
        // the line with one marker per price at its real place
        float lineY = h - 22 * d, l = pad + 8 * d, r = w - pad - 8 * d;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * d); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(0xFFE4DEFB);
        c.drawLine(l, lineY, r, lineY, p);
        p.setStyle(Paint.Style.FILL);
        for (Mark m : marks) {
            float x = markX(m, l, r);
            boolean on = m.id().equals(glowId);
            p.setColor(on ? GLOW : primary);
            c.drawCircle(x, lineY, 7.5f * d, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(x, lineY, 4.5f * d, p);
        }
    }

    private float sd() { return getResources().getDisplayMetrics().scaledDensity; }

    private float markX(Mark m, float l, float r) { return max <= min ? (l + r) / 2f : l + (float) ((m.price() - min) / (max - min)) * (r - l); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        float x = e.getX(), y = e.getY();
        if (chooseMode) {
            if (chooseHit.contains(x, y)) { performClick(); if (listener != null) listener.choose(); }
            return true;
        }
        float pad = 16 * d, l = pad + 8 * d, r = getWidth() - pad - 8 * d, lineY = getHeight() - 22 * d;
        String hit = null;
        float best = 22 * d;                                                   // markers: nearest within 22 dp
        if (Math.abs(y - lineY) < 24 * d) for (Mark m : marks) { float dx = Math.abs(markX(m, l, r) - x); if (dx < best) { best = dx; hit = m.id(); } }
        if (hit == null) for (int i = 0; i < cellHit.size() && i < cells.size(); i++) if (cellHit.get(i).contains(x, y)) { hit = cells.get(i).id(); break; }
        if (hit != null) {
            glowId = hit.equals(glowId) ? null : hit;                          // tap again = off
            invalidate();
            if (listener != null) listener.glow(glowId);
        }
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }
}
