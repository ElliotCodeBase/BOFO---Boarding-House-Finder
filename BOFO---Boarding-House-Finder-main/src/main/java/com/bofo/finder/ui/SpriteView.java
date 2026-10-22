package com.bofo.finder.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

/** Plays a horizontal sprite sheet in a loop (frames laid out left to right) by moving the image viewport. */
public final class SpriteView extends ImageView {
    private final Timeline timeline;
    private int frame = 0;

    /**
     * @param resource    classpath path of the sheet, e.g. "/com/bofo/finder/mascot/bofo-jump-spritesheet.png"
     * @param frames      number of frames in the sheet
     * @param frameSize   width = height of one frame in pixels
     * @param fps         playback speed
     * @param displaySize on-screen size (the frame is scaled to this)
     */
    public SpriteView(String resource, int frames, int frameSize, double fps, double displaySize) {
        Image sheet = new Image(SpriteView.class.getResourceAsStream(resource));
        setImage(sheet);
        setSmooth(true);
        setPreserveRatio(true);
        setFitWidth(displaySize);
        setViewport(new Rectangle2D(0, 0, frameSize, frameSize));
        timeline = new Timeline(new KeyFrame(Duration.seconds(1.0 / fps), e -> {
            frame = (frame + 1) % frames;
            setViewport(new Rectangle2D(frame * frameSize, 0, frameSize, frameSize));
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    public SpriteView play() { timeline.play(); return this; }
    public void stop() { timeline.stop(); }

    /** The BOFO jump loop used on the loading screen (16 frames, ~1 s per jump). */
    public static SpriteView bofoJump(double displaySize) {
        return new SpriteView("/com/bofo/finder/mascot/bofo-jump-spritesheet.png", 16, 256, 16, displaySize).play();
    }
}
