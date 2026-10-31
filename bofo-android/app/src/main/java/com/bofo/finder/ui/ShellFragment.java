package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentShellBinding;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.Favorites;

/**
 * Student shell: tab container + bottom navigation (Home, Explore, Saved, Chats, Profile).
 * Home, Explore and Saved are real since Stage 4; Chats is real since Stage 7; owner tabs since 8b; admin tabs since 8h (same shell, other menu).
 */
public class ShellFragment extends Fragment {

    private FragmentShellBinding binding;
    /** Last tab shown by this instance (0 = none yet). Lets Back from a pushed screen (detail, edit profile) return to the same tab. */
    private int selectedTab = 0;
    private boolean owner, admin;          // owners get My places / Chats / Profile (8b); admins get Queue / Reports / Payments / Profile (8h)

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A fresh shell means a fresh sign-in: forget the previous user's campus, filters and saved places.
        if (savedInstanceState == null) {
            BrowseState.reset();
            Favorites.reset();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentShellBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Floating tab bar: lift the bar and the mascot button above the system gesture area (Step 1, v2.0.19).
        final float dp = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (binding != null) {
                LinearLayout.LayoutParams nav = (LinearLayout.LayoutParams) binding.bottomNav.getLayoutParams();
                nav.bottomMargin = Math.round(12 * dp) + bars.bottom;
                binding.bottomNav.setLayoutParams(nav);
                FrameLayout.LayoutParams fab = (FrameLayout.LayoutParams) binding.bofoFab.getLayoutParams();
                fab.bottomMargin = Math.round(96 * dp) + bars.bottom;
                binding.bofoFab.setLayoutParams(fab);
            }
            return insets;
        });
        owner = AuthRouter.role == com.bofo.finder.model.Role.OWNER;
        admin = AuthRouter.role == com.bofo.finder.model.Role.ADMIN;
        if (owner) { binding.bottomNav.getMenu().clear(); binding.bottomNav.inflateMenu(R.menu.bottom_nav_owner); }
        if (admin) {
            binding.bottomNav.getMenu().clear(); binding.bottomNav.inflateMenu(R.menu.bottom_nav_admin);
            binding.bofoFab.setVisibility(View.GONE);                  // the Ask BOFO assistant is for students looking for a place
        }
        binding.bofoFab.setOnClickListener(v -> new BofoPanelSheet().show(getChildFragmentManager(), "bofo"));
        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        if (selectedTab != 0) {
            // Back from a pushed screen: the child fragment is still there, only the bar has to show its tab again.
            binding.bottomNav.getMenu().findItem(selectedTab).setChecked(true);
        } else if (savedInstanceState == null) {
            binding.bottomNav.setSelectedItemId(admin ? R.id.tab_queue : owner ? R.id.tab_listings : R.id.tab_home);       // first open
        }                                                              // after rotation the system restores both
    }

    private void showTab(int id) {
        selectedTab = id;
        Fragment f;
        if (id == R.id.tab_profile) {
            f = new ProfileFragment();
        } else if (id == R.id.tab_queue) {
            f = new QueueFragment();
        } else if (id == R.id.tab_reports) {
            f = new ReportsFragment();
        } else if (id == R.id.tab_admin_payments) {
            f = new AdminPaymentsFragment();
        } else if (id == R.id.tab_listings) {
            f = new ListingsFragment();
        } else if (id == R.id.tab_explore) {
            f = new ExploreFragment();
        } else if (id == R.id.tab_saved) {
            f = new SavedFragment();
        } else if (id == R.id.tab_chats) {
            f = new ChatsFragment();
        } else {
            f = new HomeFragment();
        }
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.tab_container, f)
                .commit();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
