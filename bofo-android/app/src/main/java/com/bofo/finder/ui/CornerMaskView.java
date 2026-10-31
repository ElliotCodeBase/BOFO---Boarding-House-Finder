package com.bofo.finder.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bofo.finder.R;

/**
 * Paints the page colour outside a rounded rectangle, so a fast SurfaceView map looks rounded without the slow texture mode.
 * Sits on top of the map; never takes touches.
 */
public class CornerMaskView extends android.view.View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float radius;

    public CornerMaskView(@NonNull Context c) { this(c, null); }

    public CornerMaskView(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        paint.setColor(ContextCompat.getColor(c, R.color.bofo_bg));
        radius = 27 * c.getResources().getDisplayMetrics().density;      // the card is 28 dp with a 1 dp outline
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // Four small corner pieces instead of one huge "everything outside the card" shape: that inverse fill was replayed on the GPU every frame.
        path.reset();
        float r = Math.min(radius, Math.min(w, h) / 2f);
        path.moveTo(0, 0); path.lineTo(r, 0); path.arcTo(new RectF(0, 0, 2 * r, 2 * r), 270, -90); path.close();
        path.moveTo(w, 0); path.lineTo(w, r); path.arcTo(new RectF(w - 2 * r, 0, w, 2 * r), 0, -90); path.close();
        path.moveTo(w, h); path.lineTo(w - r, h); path.arcTo(new RectF(w - 2 * r, h - 2 * r, w, h), 90, -90); path.close();
        path.moveTo(0, h); path.lineTo(0, h - r); path.arcTo(new RectF(0, h - 2 * r, 2 * r, h), 180, -90); path.close();
        path.setFillType(Path.FillType.WINDING);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) { canvas.drawPath(path, paint); }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) { return false; }
}
