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
        path.reset();
        path.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
        path.setFillType(Path.FillType.INVERSE_WINDING);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) { canvas.drawPath(path, paint); }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) { return false; }
}
