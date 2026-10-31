package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentShellBinding;
import com.bofo.finder.model.BofoAnswer;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.LocationHelper;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.ExploreHandoff;
import com.bofo.finder.util.Favorites;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shell for all three user types: tab container + the dock bar (same look and tabs as the JavaFX app).
 * Student: Home, Explore, Saved, Chats, Profile.
 * Owner: Dashboard, Listings, Create (raised button, opens the new-listing form), Chats, Profile.
 * Admin: Queue, Payments, Reports, Profile.
 */
public class ShellFragment extends Fragment {

    private FragmentShellBinding binding;
    /** Last tab shown by this instance (0 = none yet). Lets Back from a pushed screen (detail, edit profile) return to the same tab. */
    private int selectedTab = 0;
    private OnBackPressedCallback closeBofo;      // Back closes the Ask BOFO card first
    private boolean owner, admin;
    private LocationHelper loc;                   // reads the phone position for Ask BOFO
    private boolean askedLocation;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loc = new LocationHelper(this);
        // A fresh shell means a fresh sign-in: forget the previous user's campus, filters and saved places.
        if (savedInstanceState == null) {
            BrowseState.reset();
            Favorites.reset();
            FloatingBofoView.forgetPosition();
        } else {
            selectedTab = savedInstanceState.getInt("dockTab", 0);     // the dock is not restored by the system, so the tab is kept here
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("dockTab", selectedTab);
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
        owner = AuthRouter.role == com.bofo.finder.model.Role.OWNER;
        admin = AuthRouter.role == com.bofo.finder.model.Role.ADMIN;
        binding.dock.setTabs(tabsFor());
        if (admin) binding.bofoFab.setVisibility(View.GONE);           // the Ask BOFO assistant is for students looking for a place

        // Floating tab bar: lift the bar and the mascot button above the system gesture area.
        final float dp = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (binding != null) {
                LinearLayout.LayoutParams nav = (LinearLayout.LayoutParams) binding.dock.getLayoutParams();
                nav.bottomMargin = Math.round(12 * dp) + bars.bottom;
                binding.dock.setLayoutParams(nav);
                int above = binding.dock.barHeightPx() + Math.round(16 * dp) + bars.bottom;      // bar + its bottom margin + a little air
                FrameLayout.LayoutParams fab = (FrameLayout.LayoutParams) binding.bofoFab.getLayoutParams();
                fab.bottomMargin = above + Math.round(6 * dp);
                binding.bofoFab.setLayoutParams(fab);
                binding.bofoFab.setBounds(Math.round(8 * dp), above);      // stays above the tab bar
                binding.bofoChat.setBottomOffset(above);
            }
            return insets;
        });

        closeBofo = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { if (binding != null) binding.bofoChat.close(); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), closeBofo);
        binding.bofoChat.setOnClosed(() -> {
            closeBofo.setEnabled(false);
            if (binding != null) binding.bofoFab.setChatOpen(false);   // the button pops back
        });
        binding.bofoFab.setOnClickListener(v -> openBofo());
        binding.bofoChat.setOnAction(this::runBofoAction);
        binding.bofoChat.setLocationGate(this::ensureLocationForChat);

        binding.dock.setListener(new DockBarView.Listener() {
            @Override public void onTab(int id) { showTab(id); refreshBadge(); }
            @Override public void onAction(int id) {
                if (id == R.id.tab_create) NavHostFragment.findNavController(ShellFragment.this).navigate(R.id.listingFormFragment);
            }
        });

        if (selectedTab != 0) {
            // Back from a pushed screen, or after rotation: the child fragment is still there, only the bar has to show its tab again.
            binding.dock.select(selectedTab, false);
        } else if (savedInstanceState == null) {
            int first = admin ? R.id.tab_queue : owner ? R.id.tab_dashboard : R.id.tab_home;       // first open (same as the JavaFX app: the first tab)
            binding.dock.select(first, false);
            showTab(first);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshBadge();
    }

    /** Tabs per user type, in the same order and with the same icons as the JavaFX app. */
    private List<DockBarView.Tab> tabsFor() {
        List<DockBarView.Tab> t = new ArrayList<>();
        if (admin) {
            t.add(tab(R.id.tab_queue, R.drawable.ic_dock_queue, getString(R.string.tab_queue), false, false));
            t.add(tab(R.id.tab_admin_payments, R.drawable.ic_dock_payments, getString(R.string.tab_admin_payments), false, false));
            t.add(tab(R.id.tab_reports, R.drawable.ic_dock_reports, getString(R.string.tab_reports), false, false));
            t.add(tab(R.id.tab_profile, R.drawable.ic_dock_profile, getString(R.string.tab_profile), false, false));
        } else if (owner) {
            t.add(tab(R.id.tab_dashboard, R.drawable.ic_dock_dashboard, getString(R.string.dock_dashboard), false, false));
            t.add(tab(R.id.tab_listings, R.drawable.ic_dock_listings, getString(R.string.dock_listings), false, false));
            t.add(tab(R.id.tab_create, R.drawable.ic_dock_plus, getString(R.string.dock_create), true, false));
            t.add(tab(R.id.tab_chats, R.drawable.ic_dock_chats, getString(R.string.tab_chats), false, true));
            t.add(tab(R.id.tab_profile, R.drawable.ic_dock_profile, getString(R.string.tab_profile), false, false));
        } else {
            t.add(tab(R.id.tab_home, R.drawable.ic_dock_home, getString(R.string.tab_home), false, false));
            t.add(tab(R.id.tab_explore, R.drawable.ic_dock_explore, getString(R.string.tab_explore), false, false));
            t.add(tab(R.id.tab_saved, R.drawable.ic_dock_saved, getString(R.string.tab_saved), false, false));
            t.add(tab(R.id.tab_chats, R.drawable.ic_dock_chats, getString(R.string.tab_chats), false, true));
            t.add(tab(R.id.tab_profile, R.drawable.ic_dock_profile, getString(R.string.tab_profile), false, false));
        }
        return t;
    }

    private static DockBarView.Tab tab(int id, int icon, String label, boolean action, boolean chat) {
        return new DockBarView.Tab(id, icon, label, action, chat);
    }

    /** Unread count on the Chats icon (students and owners). A failed check just leaves the badge as it was. */
    private void refreshBadge() {
        if (binding == null || admin) return;
        Async.run(() -> new ChatService().totalUnread(), (Integer n) -> {
            if (binding != null) binding.dock.setBadge(n == null ? 0 : n);
        }, e -> { });
    }

    /** Home search bar: Enter on a place name switches to the Explore tab (Explore reads ExploreHandoff when it opens). */
    void openExplore() {
        if (binding == null) return;
        binding.dock.select(R.id.tab_explore, true);
        showTab(R.id.tab_explore);
    }

    /** Tap on the floating button: it fades out and the chat card pops from where it was. */
    private void openBofo() {
        if (binding == null || binding.bofoChat.isOpen()) return;
        FloatingBofoView f = binding.bofoFab;
        float cx = f.getLeft() + f.getTranslationX() + f.getWidth() / 2f;
        float cy = f.getTop() + f.getTranslationY() + f.getHeight() / 2f;
        refreshDeviceLocation();
        f.setChatOpen(true);
        binding.bofoChat.open(cx, cy);
        closeBofo.setEnabled(true);
    }

    /**
     * Gives Ask BOFO the phone's position without the user doing anything. Respects Settings > Use my location and the permission:
     * only reads when the permission is already granted. The permission and the phone's Location popup come from ensureLocationForChat. Failures are silent.
     */
    private void refreshDeviceLocation() {
        if (getContext() == null || loc == null) return;
        if (!AppPrefs.useLocation(requireContext())) { BrowseState.deviceLat = null; BrowseState.deviceLng = null; return; }
        if (!LocationHelper.granted(requireContext())) return;        // never asks here: the chat asks when a question needs the position
        if (askedLocation && BrowseState.deviceLat == null) return;
        askedLocation = true;
        loc.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) { BrowseState.deviceLat = lat; BrowseState.deviceLng = lng; }
            @Override public void off() { }
            @Override public void denied() { }
            @Override public void failed() { }
        });
    }

    /** A question needs the phone position and none is known: ask for the permission, show the phone's \"Turn on location?\" popup, read one position. */
    private void ensureLocationForChat(BofoChatOverlay.GateResult r) {
        if (getContext() == null || loc == null) { r.done(false, 0); return; }
        final boolean[] fin = {false};
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] timeout = new Runnable[1];
        final java.util.function.BiConsumer<Boolean, Integer> finish = (ok, res) -> {
            if (fin[0]) return;
            fin[0] = true; h.removeCallbacks(timeout[0]);
            r.done(ok, res);
        };
        timeout[0] = () -> finish.accept(false, R.string.loc_failed);          // never leave the chat waiting
        h.postDelayed(timeout[0], 20_000);
        askedLocation = true;
        loc.requestOnce(new LocationHelper.Callback() {
            @Override public void got(double lat, double lng) { BrowseState.deviceLat = lat; BrowseState.deviceLng = lng; finish.accept(true, 0); }
            @Override public void off() { finish.accept(false, R.string.loc_off); }
            @Override public void denied() { finish.accept(false, R.string.loc_denied); }
            @Override public void failed() { finish.accept(false, R.string.loc_failed); }
            @Override public void servicesOff() { finish.accept(false, R.string.loc_services_off); }
        });
    }

    /** Ask BOFO action chips / result cards. The chat card closes first, then the app navigates (map with only those pins, detail, Explore with filters). */
    private void runBofoAction(BofoAnswer.Action a, BofoAnswer ans) {
        if (binding == null) return;
        binding.bofoChat.close();
        switch (a.type()) {
            case "show_on_map": {
                List<String> ids = a.ids() != null && !a.ids().isEmpty() ? a.ids() : a.id() != null ? List.of(a.id()) : new ArrayList<String>();
                if (ids.isEmpty()) return;
                BofoAnswer.Result one = ids.size() == 1 ? ans.result(ids.get(0)) : null;
                if (one != null) MapFragment.open(this, one.id(), one.lat(), one.lng());
                else MapFragment.open(this, ids.toArray(new String[0]));
                break;
            }
            case "directions":                      // "show me the path": drawn on the main map, from the chosen college (ids = ["me"] -> from the phone)
                if (a.id() != null) MapFragment.openRoute(this, a.id(), a.ids() != null && a.ids().contains("me"));
                break;
            case "open_detail":
                if (a.id() != null) BhDetailFragment.open(this, a.id());
                break;
            case "apply_filters":
                applyBofoFilters(ans.filters());
                break;
            default:
                break;
        }
    }

    /** filters from the answer -> the Explore filters (same names the filter sheet uses), then Explore opens and reloads. */
    private void applyBofoFilters(JSONObject f) {
        if (f == null) return;
        BrowseState.clearFilters();
        if (f.has("max_price")) BrowseState.maxPrice = f.optDouble("max_price");
        if (f.optBoolean("free_wifi")) BrowseState.amenityNames.add("Free Wi-Fi");
        if (f.optBoolean("free_water")) BrowseState.amenityNames.add("Free water");
        if (f.optBoolean("free_electricity")) BrowseState.amenityNames.add("Free electricity");
        JSONArray am = f.optJSONArray("amenities");
        if (am != null) for (int i = 0; i < am.length(); i++) { String n = am.optString(i, ""); if (!n.isEmpty()) BrowseState.amenityNames.add(n); }
        String text = f.optString("text", "");
        ExploreHandoff.set(text.isEmpty() ? null : text);
        BrowseState.bump();
        openExplore();
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
        } else if (id == R.id.tab_dashboard) {
            f = new DashboardFragment();
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
        if (loc != null) loc.stop();
        binding = null;
        super.onDestroyView();
    }
}
