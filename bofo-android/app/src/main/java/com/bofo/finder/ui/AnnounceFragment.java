package com.bofo.finder.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentAnnounceBinding;
import com.bofo.finder.model.BhGroup;
import com.bofo.finder.model.GroupMessage;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.BhGroupService;
import com.bofo.finder.util.AiFlag;
import com.bofo.finder.util.AnnounceDraftState;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.BoardingRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * v2.2.0 step 4: Announce form. A title, a message and the houses to send it to (one or several of the owner's houses; the chat's menu
 * opens it with that house already ticked). Posting pins the announcement in every chosen house (the database keeps at most 3 pinned).
 * <p>v2.4.3: <b>Write for me</b> (hidden when the admin switched the AI off) opens {@link AnnounceAiSheet}; its <b>Use this</b> copies a draft into
 * the title and message fields (asking first when they already have text). It never posts: the owner reads the text and presses Post, and
 * {@code BoardingRules.checkAnnouncement} still runs then.</p>
 */
public class AnnounceFragment extends Fragment {

    /** Argument: id of the house to tick first (optional). */
    public static final String ARG_BH = "bhId";

    private FragmentAnnounceBinding b;
    private final BhGroupService svc = new BhGroupService();
    private final List<CheckBox> boxes = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();
    private boolean posting;
    private int token;

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        AnnounceAiSheet.listen(this, this::onDraftChosen);               // survives rotation; called while this screen is started
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentAnnounceBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.post.setOnClickListener(v -> post());
        b.writeForMe.setOnClickListener(v -> AnnounceAiSheet.show(this));
        b.writeForMe.setVisibility(showWriteForMe(AiFlag.on()) ? View.VISIBLE : View.GONE);
        b.selectAll.setOnClickListener(v -> {
            boolean all = allTicked();
            for (CheckBox c : boxes) c.setChecked(!all);
            refreshSelectAll();
        });
        b.bodyInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int n, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int n, int d) { }
            @Override public void afterTextChanged(Editable e) { if (b != null) b.counter.setText(e.length() + " / " + BhGroupService.MAX_LEN); }
        });
        b.counter.setText("0 / " + BhGroupService.MAX_LEN);
        loadHouses();
    }

    @Override
    public void onResume() {
        super.onResume();
        AiFlag.refresh(on -> { if (b != null) b.writeForMe.setVisibility(showWriteForMe(on) ? View.VISIBLE : View.GONE); });   // same as ShellFragment
    }

    /** v2.4.4: shown when the AI is on, and always for admins (the server and the sheet already exempt admins when the AI is switched off). */
    private static boolean showWriteForMe(boolean aiOn) {
        return aiOn || (BrowseState.me != null && BrowseState.me.role() == Role.ADMIN);
    }

    /** Use this: put the draft in the form (clipped to 120 / 1000). Existing text is replaced only after the owner agrees. Never posts. */
    private void onDraftChosen(String title, String message) {
        if (b == null) return;
        final AnnounceDraftState.Use u = AnnounceDraftState.prepareUse(title, message);
        if (u == null) return;
        if (AnnounceDraftState.needsReplaceConfirm(b.titleInput.getText().toString(), b.bodyInput.getText().toString())) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.announce_replace_title)
                    .setMessage(R.string.announce_replace_body)
                    .setPositiveButton(R.string.announce_replace_yes, (d, w) -> applyDraft(u))
                    .setNegativeButton(R.string.announce_replace_no, null)
                    .show();
        } else {
            applyDraft(u);
        }
    }

    private void applyDraft(AnnounceDraftState.Use u) {
        if (b == null) return;
        b.titleInput.setText(u.title);
        b.bodyInput.setText(u.message);                                  // the TextWatcher refreshes the counter
        b.titleInput.setSelection(b.titleInput.getText().length());
        showError("");
        ListArea.toast(this, getString(R.string.announce_draft_added));
    }

    private void loadHouses() {
        final int my = ++token;
        b.loading.setVisibility(View.VISIBLE);
        Async.run(() -> svc.myGroups(), (List<BhGroup> groups) -> {
            if (my != token || b == null) return;
            b.loading.setVisibility(View.GONE);
            String first = getArguments() == null ? "" : getArguments().getString(ARG_BH, "");
            List<BhGroup> mine = new ArrayList<>();
            for (BhGroup g : groups) if (GroupMessage.OWNER.equals(g.myRole())) mine.add(g);
            b.houses.removeAllViews();
            boxes.clear();
            ids.clear();
            for (BhGroup g : mine) {
                CheckBox c = new CheckBox(requireContext());
                c.setText(g.bhName());
                c.setTextSize(15);
                c.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
                c.setChecked(g.bhId().equals(first) || mine.size() == 1);
                c.setOnCheckedChangeListener((x, on) -> refreshSelectAll());
                b.houses.addView(c);
                boxes.add(c);
                ids.add(g.bhId());
            }
            b.housesEmpty.setVisibility(mine.isEmpty() ? View.VISIBLE : View.GONE);
            b.selectAll.setVisibility(mine.size() > 1 ? View.VISIBLE : View.GONE);
            b.post.setEnabled(!mine.isEmpty());
            refreshSelectAll();
        }, e -> {
            if (my != token || b == null) return;
            b.loading.setVisibility(View.GONE);
            showError(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.owner_boarding_load_error), e));
        });
    }

    private boolean allTicked() {
        if (boxes.isEmpty()) return false;
        for (CheckBox c : boxes) if (!c.isChecked()) return false;
        return true;
    }

    private void refreshSelectAll() {
        if (b != null) b.selectAll.setText(allTicked() ? R.string.announce_select_none : R.string.announce_select_all);
    }

    private List<String> chosen() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isChecked()) out.add(ids.get(i));
        return out;
    }

    private void showError(String msg) {
        b.error.setText(msg);
        b.error.setVisibility(msg == null || msg.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void post() {
        if (posting || b == null) return;
        final String title = b.titleInput.getText().toString().trim();
        final String body = b.bodyInput.getText().toString().trim();
        final List<String> to = chosen();
        switch (BoardingRules.checkAnnouncement(title, body, to.size())) {
            case NO_TITLE: showError(getString(R.string.announce_err_title)); return;
            case NO_BODY: showError(getString(R.string.announce_err_body)); return;
            case NO_HOUSE: showError(getString(R.string.announce_err_house)); return;
            case TOO_MANY: showError(getString(R.string.announce_err_many)); return;
            default: break;
        }
        showError("");
        posting = true;
        b.post.setEnabled(false);
        b.post.setText(R.string.announce_posting);
        Async.run(() -> svc.postAnnouncement(to, title, body), (List<String> done) -> {
            posting = false;
            if (b == null) return;
            ListArea.toast(this, getResources().getQuantityString(R.plurals.announce_done, to.size(), to.size()));
            NavHostFragment.findNavController(this).navigateUp();
        }, e -> {
            posting = false;
            if (b == null) return;
            b.post.setEnabled(true);
            b.post.setText(R.string.announce_post);
            showError(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.announce_error), e));
        });
    }

    @Override
    public void onDestroyView() {
        token++;
        b = null;
        super.onDestroyView();
    }
}
