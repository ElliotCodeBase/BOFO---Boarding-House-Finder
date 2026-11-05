package com.bofo.finder.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentShellBinding;
import com.bofo.finder.model.BofoAnswer;
import com.bofo.finder.model.GroupMessage;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhGroupService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.service.PushService;
import com.bofo.finder.util.BoardingRules;
import com.google.android.material.snackbar.Snackbar;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.LocationHelper;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.ExploreHandoff;
import com.bofo.finder.util.Favorites;

import org.json.JSONArray;
import org.json.JSONObject;

import com.bofo.finder.model.BoardingHouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Shell for all three user types: tab container + the dock bar (same look and tabs as the JavaFX app).
 * Student: Home, Explore, Boarding (centre), Chats, Profile. (Saved moved to Profile > Saved places in v2.2.0.)
 * Owner: Dashboard (v2.4.9: includes your listings), Messages, Create (raised button, opens the new-listing form), Groups (v2.4.8: the owner's Boarding groups; direct chats open from its header), Profile.
 * Admin: Home, Queue, Payments, Reports, Profile.
 */
public class ShellFragment extends Fragment {

    private FragmentShellBinding binding;
    /** Last tab shown by this instance (0 = none yet). Lets Back from a pushed screen (detail, edit profile) return to the same tab. */
    private int selectedTab = 0;
    private boolean chatFull;                 // v2.2.3: the student's group chat is open: no tab bar, no Ask BOFO button
    private int bottomInset;                  // system bar height, used while the tab bar is hidden
    private OnBackPressedCallback closeBofo;      // Back closes the Ask BOFO card first
    private boolean owner, admin;
    private int pendingFilter = -1;
    private LocationHelper loc;                   // reads the phone position for Ask BOFO
    private boolean askedLocation;
    /** v2.3.0: Android 13+ asks before notifications may show. The answer (yes or no) ends in a sync: it registers this phone only when allowed. */
    private final ActivityResultLauncher<String> notifPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            granted -> { if (isAdded()) PushService.syncAsync(requireContext(), AuthRouter.realRole.db()); });

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
        if (admin) {                                                    // v2.4.14: always show which mode and which account this is
            String em = com.bofo.finder.service.AuthService.get().email();
            binding.adminBanner.setText(em.isEmpty() ? getString(R.string.admin_banner) : getString(R.string.admin_banner_as, em));
            binding.adminBanner.setVisibility(View.VISIBLE);
        }
        if (admin) binding.bofoFab.setVisibility(View.GONE);           // the Ask BOFO assistant is for students looking for a place
        if (admin && AuthRouter.takeMfaPrompt()) view.post(() -> AdminMfaSheet.show(this, null));      // v2.0.119: ask for the 6-digit code once per sign-in

        // Floating tab bar: lift the bar and the mascot button above the system gesture area.
        final float dp = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            bottomInset = bars.bottom;
            if (binding != null) {
                applyChatFull();
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

        applyChatFull();
        if (selectedTab != 0) {
            // Back from a pushed screen, or after rotation: the child fragment is still there, only the bar has to show its tab again.
            binding.dock.select(selectedTab, false);
        } else if (savedInstanceState == null) {
            int first = admin ? R.id.tab_admin_home : owner ? R.id.tab_dashboard : R.id.tab_home;       // first open (same as the JavaFX app: the first tab)
            binding.dock.select(first, false);
            showTab(first);
        }
        setupPush();
    }

    /** v2.3.0: phone notifications. Asks the system permission once (Android 13+), then makes sure the server knows this phone (and its account type). */
    private void setupPush() {
        final android.content.Context app = requireContext().getApplicationContext();
        if (!AppPrefs.pushEnabled(app)) return;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (!PushService.askedPermission(app) && PushService.firebaseReady(app)) {
                PushService.markAskedPermission(app);
                binding.getRoot().post(() -> { if (isAdded()) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS); });
            }
            return;
        }
        PushService.syncAsync(app, AuthRouter.realRole.db());
    }

    /**
     * v2.3.0: a notification was tapped. dm = that chat (Chats tab, then the chat on top), group = the boarding group chat (student: Boarding tab,
     * owner: the owner's group chat screen), report = the Reports tab, idreview = the Queue, stale = Queue / Reports / Payments (admin only, Waiting filter). Anything that does not fit this account is ignored.
     */
    void openFromPush(String kind, String id, String title, String place) {
        if (binding == null || id == null || id.isEmpty()) return;
        if (chatFull) { chatFull = false; applyChatFull(); }
        NavController nav = NavHostFragment.findNavController(this);
        if ("dm".equals(kind) && !admin) {
            binding.dock.select(R.id.tab_chats, true);      // v2.4.9: owners have a Messages tab again (same Chats list)
            showTab(R.id.tab_chats);
            ChatFragment.open(this, id, title, place);
        } else if ("group".equals(kind) && owner) {
            nav.navigate(R.id.boardingChatFragment, BoardingChatFragment.args(id, title, 0, GroupMessage.OWNER, true, false));
        } else if ("group".equals(kind) && !admin) {
            binding.dock.select(R.id.tab_boarding, true);
            showTab(R.id.tab_boarding);
        } else if (com.bofo.finder.util.PushRules.adminOnly(kind) && admin && AuthRouter.realRole == com.bofo.finder.model.Role.ADMIN) {
            // v2.6.0: report -> Reports; idreview -> Queue; stale -> the tab the server chose. Always with the Waiting filter.
            int target = com.bofo.finder.util.PushRules.adminTab(kind, place);
            if (target == com.bofo.finder.util.PushRules.TAB_REPORTS) openAdminTab(R.id.tab_reports, 0);
            else if (target == com.bofo.finder.util.PushRules.TAB_PAYMENTS) openAdminTab(R.id.tab_admin_payments, com.bofo.finder.util.AdminListRules.WAITING);
            else if (target == com.bofo.finder.util.PushRules.TAB_QUEUE) openAdminTab(R.id.tab_queue, com.bofo.finder.util.AdminListRules.WAITING);
        }
        refreshBadge();
    }

    /** v2.2.3: BoardingFragment calls this: true while the group chat is on screen (tab bar and Ask BOFO button hidden), false otherwise. */
    void setChatFull(boolean on) { chatFull = on; applyChatFull(); }

    private void applyChatFull() {
        if (binding == null) return;
        binding.dock.setVisibility(chatFull ? View.GONE : View.VISIBLE);
        binding.bofoFab.setVisibility(chatFull || admin || !com.bofo.finder.util.AiFlag.on() ? View.GONE : View.VISIBLE);       // v2.3.2: admin AI switch
        binding.tabContainer.setPadding(0, 0, 0, chatFull ? bottomInset : 0);       // the bar used to keep the content above the system buttons
    }

    /** v2.2.3: the Back arrow of the group chat: leaves it and opens the Home tab with the bar again. */
    void leaveBoarding() {
        chatFull = false;
        applyChatFull();
        if (binding == null) return;
        binding.dock.select(R.id.tab_home, true);
        showTab(R.id.tab_home);
        refreshBadge();
    }

    @Override
    public void onResume() {
        super.onResume();
        PushRouter.shellResumed(this);
        refreshBadge();
        com.bofo.finder.util.AiFlag.refresh(v -> {          // v2.3.2: the admin may have switched the AI off: hide the button (and close an open chat)
            if (binding == null) return;
            applyChatFull();
            if (!v && binding.bofoChat.isOpen()) binding.bofoChat.close();
        });
        showNotice();
    }

    /** Announcement ids already shown as a notice in this app run: each one notifies once (v2.2.0 step 5). */
    private static final java.util.Set<String> SHOWN = new java.util.HashSet<>();

    /**
     * Students: owner announcements I have not pressed "Got it" on show as one bar above the dock (Open = the Boarding tab). Derived from the
     * unacknowledged announcements, so there is no extra table. Errors are ignored (migration 024 may not be live yet).
     */
    private void showNotice() {
        if (binding == null || admin || owner) return;
        String me = AuthService.get().userId();
        if (me == null || me.isEmpty()) return;
        Async.run(() -> new BhGroupService().unackedAnnouncements(me), (List<GroupMessage> list) -> {
            if (binding == null || !isAdded()) return;
            List<GroupMessage> fresh = BoardingRules.newNotices(list, SHOWN);
            if (fresh.isEmpty()) return;
            String title = BoardingRules.noticeTitle(fresh);
            String text = fresh.size() == 1 ? getString(R.string.boarding_notice_one, title) : getString(R.string.boarding_notice_many, fresh.size(), title);
            Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.dock)
                    .setAction(R.string.boarding_notice_open, v -> {
                        if (binding == null) return;
                        binding.dock.select(R.id.tab_boarding, true);
                        showTab(R.id.tab_boarding);
                    })
                    .show();
        }, e -> { });
    }

    /** Tabs per user type, in the same order and with the same icons as the JavaFX app. */
    private List<DockBarView.Tab> tabsFor() {
        List<DockBarView.Tab> t = new ArrayList<>();
        if (admin) {
            t.add(tab(R.id.tab_admin_home, R.drawable.ic_dock_home, getString(R.string.tab_admin_home), false, false));      // v2.4.10: Admin Home comes first
            t.add(tab(R.id.tab_queue, R.drawable.ic_dock_queue, getString(R.string.tab_queue), false, true));              // badge = IDs waiting
            t.add(tab(R.id.tab_admin_payments, R.drawable.ic_dock_payments, getString(R.string.tab_admin_payments), false, false));
            t.add(tab(R.id.tab_reports, R.drawable.ic_dock_reports, getString(R.string.tab_reports), false, true));            // badge = open reports
            t.add(tab(R.id.tab_profile, R.drawable.ic_dock_profile, getString(R.string.tab_profile), false, false));
        } else if (owner) {
            t.add(tab(R.id.tab_dashboard, R.drawable.ic_dock_dashboard, getString(R.string.dock_dashboard), false, false));
            t.add(tab(R.id.tab_chats, R.drawable.ic_dock_chats, getString(R.string.dock_messages), false, true));       // v2.4.9: Messages replaces Listings (listings live in the Dashboard)
            t.add(tab(R.id.tab_create, R.drawable.ic_dock_plus, getString(R.string.dock_create), true, false));
            t.add(tab(R.id.tab_boarding, R.drawable.ic_dock_boarding, getString(R.string.dock_groups), false, true));      // v2.4.8: replaces Chats for owners
            t.add(tab(R.id.tab_profile, R.drawable.ic_dock_profile, getString(R.string.tab_profile), false, false));
        } else {
            t.add(tab(R.id.tab_home, R.drawable.ic_dock_home, getString(R.string.tab_home), false, false));
            t.add(tab(R.id.tab_explore, R.drawable.ic_dock_explore, getString(R.string.tab_explore), false, false));
            t.add(tab(R.id.tab_boarding, R.drawable.ic_dock_boarding, getString(R.string.tab_boarding), false, true));      // v2.2.0: centre slot (Saved moved to Profile)
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
        if (binding == null) return;
        if (admin) {                                        // v2.4.10: Queue + Reports badges (two small selects; a failure leaves the badge as it was)
            Async.run(() -> new com.bofo.finder.service.AdminCountsService().badges(), (int[] n) -> setAdminBadges(n[0], n[1]), e -> { });
            return;
        }
        if (owner) {                                        // v2.4.9: Messages tab = direct-chat unread, Groups tab = group unread
            Async.run(() -> new ChatService().totalUnread(), (Integer n) -> {
                if (binding != null) binding.dock.setBadge(R.id.tab_chats, n == null ? 0 : n);
            }, e -> { });
            Async.run(() -> new BhGroupService().totalUnread(), (Integer n) -> {
                if (binding != null) binding.dock.setBadge(R.id.tab_boarding, n == null ? 0 : n);
            }, e -> { });                                   // migration 024 may not be live: the badge just stays off
            return;
        }
        Async.run(() -> new ChatService().totalUnread(), (Integer n) -> {
            if (binding != null) binding.dock.setBadge(n == null ? 0 : n);
        }, e -> { });
        Async.run(() -> new BhGroupService().totalUnread(), (Integer n) -> {
            if (binding != null) binding.dock.setBadge(R.id.tab_boarding, n == null ? 0 : n);
        }, e -> { });                                       // migration 024 may not be live: the badge just stays off
    }

    /** Home search bar: Enter on a place name switches to the Explore tab (Explore reads ExploreHandoff when it opens). */
    void openExplore() {
        if (binding == null) return;
        binding.dock.select(R.id.tab_explore, true);
        showTab(R.id.tab_explore);
    }

    /** v2.0.82: Explore map \"Compare\" tray: the chat card opens from the Ask BOFO button with a compare table of these houses. */
    void showCompare(List<BoardingHouse> hs) {
        if (binding == null || hs == null || hs.size() < 2) return;
        if (!com.bofo.finder.util.AiFlag.on()) return;                       // v2.3.2: AI switched off by an admin
        FloatingBofoView f = binding.bofoFab;
        float cx = f.getLeft() + f.getTranslationX() + f.getWidth() / 2f;
        float cy = f.getTop() + f.getTranslationY() + f.getHeight() / 2f;
        if (!binding.bofoChat.isOpen()) { refreshDeviceLocation(); f.setChatOpen(true); closeBofo.setEnabled(true); }
        binding.bofoChat.openCompare(cx, cy, hs);
    }

    /** Tap on the floating button: it fades out and the chat card pops from where it was. */
    private void openBofo() {
        if (binding == null || binding.bofoChat.isOpen() || !com.bofo.finder.util.AiFlag.on()) return;
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

    /** v2.4.10: Admin Home cards open a tab. The dock is moved too (select() never calls the listener). */
    public void openAdminTab(int id) { openAdminTab(id, -1); }

    /** v2.4.13: {@code filter} is handed to the list (Queue / Payments: chip 0-2; Reports: its filter index); -1 = the screen's own default. */
    public void openAdminTab(int id, int filter) {
        if (binding == null || !admin) return;
        pendingFilter = filter;
        binding.dock.select(id, true);
        showTab(id);
        pendingFilter = -1;
    }

    /** v2.4.10: Queue / Reports badges; a negative (unknown) count hides the badge. */
    public void setAdminBadges(int ids, int reports) {
        if (binding == null || !admin) return;
        binding.dock.setBadge(R.id.tab_queue, com.bofo.finder.util.AdminHomeRules.badge(ids));
        binding.dock.setBadge(R.id.tab_reports, com.bofo.finder.util.AdminHomeRules.badge(reports));
    }

    private void showTab(int id) {
        if (id == selectedTab && getChildFragmentManager().findFragmentById(R.id.tab_container) != null) return;    // v2.0.97: tapping the open tab does not rebuild it
        selectedTab = id;
        Fragment f;
        if (id == R.id.tab_profile) {
            f = new ProfileFragment();
        } else if (id == R.id.tab_admin_home) {
            f = new AdminHomeFragment();
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
        } else if (id == R.id.tab_boarding) {
            f = owner ? new OwnerBoardingFragment() : new BoardingFragment();      // v2.4.8: the owner's tab lists his groups
        } else if (id == R.id.tab_chats) {
            f = new ChatsFragment();
        } else {
            f = new HomeFragment();
        }
        if (pendingFilter >= 0 && (id == R.id.tab_queue || id == R.id.tab_reports || id == R.id.tab_admin_payments)) {
            Bundle fa = new Bundle();
            fa.putInt("filter", pendingFilter);
            f.setArguments(fa);
        }
        androidx.fragment.app.FragmentTransaction tx = getChildFragmentManager().beginTransaction().setReorderingAllowed(true);
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) tx.setCustomAnimations(R.anim.tab_fade_in, R.anim.tab_fade_out);      // v2.0.97 soft cross-fade; v2.4.14: not when the system has animations off
        tx.replace(R.id.tab_container, f).commit();
    }

    @Override
    public void onPause() {
        PushRouter.shellPaused(this);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (loc != null) loc.stop();
        binding = null;
        super.onDestroyView();
    }
}
