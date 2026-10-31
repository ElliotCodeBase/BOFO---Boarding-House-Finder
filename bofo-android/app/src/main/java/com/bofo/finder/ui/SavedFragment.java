package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentSavedBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.BhService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Favorites;

import java.util.ArrayList;
import java.util.List;

/** Saved: the places the student hearted. Works offline from the last copy (BhService.favorites). */
public class SavedFragment extends Fragment {

    private FragmentSavedBinding b;
    private BhAdapter adapter;
    private ListArea area;
    private List<BoardingHouse> all = new ArrayList<>();
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentSavedBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        adapter = new BhAdapter(new BhAdapter.Listener() {
            @Override public void open(BoardingHouse x) { BhDetailFragment.open(SavedFragment.this, x.id()); }
            @Override public void heart(BoardingHouse x) {
                Favorites.toggle(x.id(), msg -> ListArea.toast(SavedFragment.this, msg));
                all = new ArrayList<>(all);
                all.removeIf(p -> p.id().equals(x.id()));        // un-saved: it leaves this list at once
                show();
            }
        });
        area = new ListArea(this, b.area, adapter, this::reload);
        Favorites.IDS.observe(getViewLifecycleOwner(), ids -> adapter.setFavorites(ids));
        Favorites.load(this::reload);            // hearts first, then the list
    }

    private void reload() {
        if (b == null) return;
        final int my = ++token;
        area.showLoading();
        Async.run(() -> new BhService().favorites(), (List<BoardingHouse> l) -> {
            if (my != token || b == null) return;
            all = l;
            show();
        }, e -> {
            if (my != token || b == null) return;
            area.showMessage(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.saved_error), e), true);
        });
    }

    private void show() {
        if (b == null) return;
        adapter.setShowDistance(false);
        if (all.isEmpty()) area.showMessage(getString(R.string.saved_empty), false);
        else area.showList(all);
    }

    @Override
    public void onDestroyView() {
        token++;
        if (area != null) area.destroy();
        b = null;
        super.onDestroyView();
    }
}
