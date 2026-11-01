package com.bofo.finder.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.navigation.NavController;

import com.bofo.finder.R;
import com.bofo.finder.databinding.ActivityMainBinding;
import com.bofo.finder.ui.AuthRouter;
import com.bofo.finder.util.AuthLinks;

/** The single Activity. Screens are Fragments hosted by the NavHost in activity_main.xml. */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding; public static boolean keepAppStill; public static int imeBottom; public static Runnable imeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Bofo);               // v2.0.97: leave the dark launch theme (manifest) before any screen is built
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (savedInstanceState == null)             // fresh launch always starts on the dark loading screen (SplashFragment puts the light colour back when it leaves)
            binding.mainRoot.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bofo_ink));

        // Edge-to-edge: keep content clear of the status bar, the navigation bar and the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            imeBottom = ime.bottom; v.setPadding(bars.left, bars.top, bars.right, keepAppStill ? bars.bottom : Math.max(bars.bottom, ime.bottom)); if (imeListener != null) imeListener.run();
            return WindowInsetsCompat.CONSUMED;
        });

        // Cold start through the sign-in link: the splash finishes it once the screens exist.
        if (savedInstanceState == null) stashLink(getIntent());
    }

    /** v2.0.102: true while a search field has the keyboard: the keyboard then slides OVER the screen instead of pushing the whole app (and the bottom bar) up. Chat boxes keep the old lift. */
    public static void holdStill(boolean on, android.view.View anyView) {
        keepAppStill = on;
        if (anyView != null) androidx.core.view.ViewCompat.requestApplyInsets(anyView);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // singleTask: the sign-in return link bofo://auth-callback arrives here while the app is already open.
        setIntent(intent);
        if (stashLink(intent)) {
            NavController nav = AuthRouter.navOf(this);
            if (nav != null && !AuthRouter.isSplash(nav)) AuthRouter.consumeLink(this, nav, null);   // on the splash, the splash picks it up
        }
    }

    private boolean stashLink(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        String uri = intent.getData().toString();
        if (!AuthLinks.isCallback(uri)) return false;
        AuthRouter.setPendingLink(uri);
        intent.setData(null);                 // never process the same link twice (rotation, re-launch)
        return true;
    }
}
