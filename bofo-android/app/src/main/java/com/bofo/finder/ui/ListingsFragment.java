package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentListingsBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.model.Profile;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.ProfileService;
import com.bofo.finder.service.VerificationService;
import com.bofo.finder.service.SupabaseClient;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Money;
import com.bofo.finder.util.VerificationRules;
import com.bofo.finder.util.VerificationRules.State;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owner > My places (8b): your listings with their status (active / unlisted / taken down). Tap a place: view it, unlist or list it again, delete it.
 * Add a place opens ListingFormFragment (8c); editing an existing place is not ported yet.
 * 8d: a banner shows the owner's verification state (hidden once verified or when it could not be read); Add a place first sends an unverified owner
 * to the verification screen, because the database refuses listings from unverified owners.
 */
public class ListingsFragment extends Fragment {

    private FragmentListingsBinding b;
    private final List<BoardingHouse> items = new ArrayList<>();
    private final Map<String, String> status = new HashMap<>();
    private ArrayAdapter<String> rows;
    private int token;
    private State verification;            // null = not known yet (offline / failed): Add a place is then allowed and the database decides

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentListingsBinding.inflate(inflater, container, false);
        rows = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, new ArrayList<String>()) {
            @NonNull @Override public View getView(int pos, @Nullable View v, @NonNull ViewGroup p) {
                View r = super.getView(pos, v, p);
                BoardingHouse x = items.get(pos);
                ((android.widget.TextView) r.findViewById(android.R.id.text1)).setText(x.name());
                ((android.widget.TextView) r.findViewById(android.R.id.text2)).setText(
                        Money.perMonth(x.price()) + "  -  " + statusText(status.get(x.id())));
                return r;
            }
        };
        b.list.setAdapter(rows);
        b.list.setOnItemClickListener((p, v, pos, id) -> { if (pos >= 0 && pos < items.size()) options(items.get(pos)); });
        b.add.setOnClickListener(v -> add());
        b.verifyBtn.setOnClickListener(v -> openVerification());
        b.dashboard.setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.dashboardFragment));
        b.boost.setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.boostFragment));
        b.payments.setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.paymentsFragment));
        b.swipe.setColorSchemeResources(R.color.bofo_primary);
        b.swipe.setOnRefreshListener(this::reload);
        return b.getRoot();
    }

    @Override
    public void onResume() { super.onResume(); reload(); }

    private void openVerification() {
        androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.verificationFragment);
    }

    private void add() {
        if (verification == null || verification == State.VERIFIED) {
            androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.listingFormFragment);
            return;
        }
        boolean pending = verification == State.PENDING;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.verify_gate_title)
                .setMessage(pending ? R.string.verify_gate_pending : R.string.verify_gate_msg)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.verify_open, (d, w) -> openVerification())
                .show();
    }

    private void showBanner() {
        if (b == null) return;
        boolean show = verification != null && verification != State.VERIFIED;
        b.verifyBanner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) b.verifyText.setText(verification == State.PENDING ? R.string.verify_banner_pending
                : verification == State.REJECTED ? R.string.verify_banner_rejected : R.string.verify_banner_unverified);
    }

    private String statusText(String s) {
        if ("unlisted".equals(s)) return getString(R.string.listing_unlisted);
        if ("taken_down".equals(s)) return getString(R.string.listing_taken_down);
        return getString(R.string.listing_active);
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        b.swipe.setRefreshing(true);
        String me = AuthService.get().userId();
        Async.run(() -> {
            List<BoardingHouse> l = new BhService().myListings(me);
            JSONArray st = SupabaseClient.get().select("boarding_houses", "owner_id=eq." + me + "&select=id,status");
            Map<String, String> m = new HashMap<>();
            for (int i = 0; i < st.length(); i++) m.put(st.getJSONObject(i).getString("id"), st.getJSONObject(i).optString("status", "active"));
            State v = null;                                     // the banner is a bonus: a failure here never hides the list
            try {
                Profile p = new ProfileService().fetchMe();
                v = VerificationRules.effective(p != null && p.verified(), new VerificationService().myLatest(me).optString("status", "unverified"));
            } catch (Exception ignored) { /* unknown */ }
            return new Object[]{l, m, v};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            @SuppressWarnings("unchecked") List<BoardingHouse> l = (List<BoardingHouse>) r[0];
            @SuppressWarnings("unchecked") Map<String, String> m = (Map<String, String>) r[1];
            items.clear(); items.addAll(l);
            status.clear(); status.putAll(m);
            verification = (State) r[2];
            showBanner();
            rows.clear();
            for (BoardingHouse x : items) rows.add(x.name());
            rows.notifyDataSetChanged();
            b.message.setText(R.string.listings_empty);
            b.message.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }, e -> {
            if (my != token || b == null) return;
            b.swipe.setRefreshing(false);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.listings_error), e));
        });
    }

    private void options(BoardingHouse x) {
        String st = status.get(x.id());
        boolean unlisted = "unlisted".equals(st), down = "taken_down".equals(st);
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add(getString(R.string.listing_view));
        acts.add(() -> BhDetailFragment.open(this, x.id()));
        if (!down) {
            labels.add(getString(R.string.listing_edit));
            acts.add(() -> { Bundle a = new Bundle(); a.putString("editId", x.id()); androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.listingFormFragment, a); });
        }
        if (!down && !unlisted) {                                 // students cannot see an unlisted place, so only active places can be boosted
            labels.add(getString(R.string.listing_boost));
            acts.add(() -> { Bundle a = new Bundle(); a.putString("bhId", x.id()); androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.boostFragment, a); });
        }
        if (!down) {                                              // a taken-down place is an admin decision: the owner cannot undo it here
            labels.add(getString(unlisted ? R.string.listing_relist : R.string.listing_unlist));
            acts.add(() -> setStatus(x, unlisted ? "active" : "unlisted"));
        }
        labels.add(getString(R.string.chats_delete));
        acts.add(() -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.listing_delete_title).setMessage(R.string.listing_delete_msg)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.chats_delete, (d, w) -> Async.run(() -> { new BhService().deleteListing(x.id()); return Boolean.TRUE; },
                        ok -> reload(), e -> ListArea.toast(this, ErrorText.full(getString(R.string.listings_error), e))))
                .show());
        new MaterialAlertDialogBuilder(requireContext()).setTitle(x.name())
                .setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run()).show();
    }

    private void setStatus(BoardingHouse x, String to) {
        Async.run(() -> { new BhService().setStatus(x.id(), to); return Boolean.TRUE; }, ok -> reload(),
                e -> ListArea.toast(this, ErrorText.full(getString(R.string.listings_error), e)));
    }

    @Override
    public void onDestroyView() { token++; b = null; super.onDestroyView(); }
}
