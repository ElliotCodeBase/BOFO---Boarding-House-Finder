package com.bofo.finder.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.navigation.NavController;

import com.bofo.finder.databinding.ActivityMainBinding;
import com.bofo.finder.ui.AuthRouter;
import com.bofo.finder.util.AuthLinks;

/** The single Activity. Screens are Fragments hosted by the NavHost in activity_main.xml. */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Edge-to-edge: keep content clear of the status bar, the navigation bar and the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        // Cold start through the sign-in link: the splash finishes it once the screens exist.
        if (savedInstanceState == null) stashLink(getIntent());
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
