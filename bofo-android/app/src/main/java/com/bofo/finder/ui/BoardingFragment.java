package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentBoardingBinding;
import com.bofo.finder.model.BhGroup;
import com.bofo.finder.service.BhGroupService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoardingRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;

import java.util.List;

/**
 * Boarding tab for students (v2.2.0). No boarding house yet: "Join your boarding house" with the owner's invite code (so outsiders
 * cannot read tenant chats). Joined: the group chat of that house ({@link BoardingChatFragment}). A student lives in one house at a time.
 */
public class BoardingFragment extends Fragment {

    private FragmentBoardingBinding b;
    private final BhGroupService svc = new BhGroupService();
    private int token;
    private boolean joining;
    private OnBackPressedCallback backCallback;      // v2.2.3: Back inside the group chat leaves to Home

    /** v2.2.3: the chat's Back arrow (and the phone's Back) go to the Home tab, which brings the tab bar back. */
    void leave() {
        Fragment p = getParentFragment();
        if (p instanceof ShellFragment) ((ShellFragment) p).leaveBoarding();
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentBoardingBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        backCallback = new OnBackPressedCallback(false) { @Override public void handleOnBackPressed() { leave(); } };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
        b.join.setOnClickListener(v -> join());
        b.code.setOnEditorActionListener((tv, action, ev) -> { if (action == EditorInfo.IME_ACTION_DONE) { join(); return true; } return false; });
        b.retry.setOnClickListener(v -> loadGroups());
        getChildFragmentManager().setFragmentResultListener(BoardingChatFragment.RESULT_LEFT, getViewLifecycleOwner(), (key, res) -> loadGroups());
        if (state == null || getChildFragmentManager().findFragmentById(R.id.chat_host) == null) loadGroups();
        else show(b.chatHost);                                      // after rotation the chat child is restored by the system
    }

    private void show(View which) {
        if (b == null) return;
        boolean chat = which == b.chatHost;                          // v2.2.3: the chat has no tab bar, it has a Back arrow
        if (backCallback != null) backCallback.setEnabled(chat);
        Fragment p = getParentFragment();
        if (p instanceof ShellFragment) ((ShellFragment) p).setChatFull(chat);
        b.chatHost.setVisibility(which == b.chatHost ? View.VISIBLE : View.GONE);
        b.joinBox.setVisibility(which == b.joinBox ? View.VISIBLE : View.GONE);
        b.loading.setVisibility(which == b.loading ? View.VISIBLE : View.GONE);
        b.errorBox.setVisibility(which == b.errorBox ? View.VISIBLE : View.GONE);
    }

    /** Asks the server which house I belong to (works offline from the last copy). */
    private void loadGroups() {
        final int my = ++token;
        show(b.loading);
        Async.run(() -> svc.myGroups(), (List<BhGroup> groups) -> {
            if (my != token || b == null) return;
            if (groups.isEmpty()) {
                removeChat();
                b.code.setText("");
                b.joinError.setVisibility(View.GONE);
                show(b.joinBox);
                return;
            }
            for (BhGroup x : groups) {
                if (BhGroupRole.isOwner(x)) {                           // v2.9.4: an owner (or anyone with 2+ groups) sees the LIST first, never one random chat
                    getChildFragmentManager().beginTransaction().setReorderingAllowed(true)
                            .replace(R.id.chat_host, new OwnerBoardingFragment()).commitAllowingStateLoss();
                    show(b.chatHost);
                    setList();
                    return;
                }
            }
            BhGroup g = groups.get(0);                                  // student: one house at a time (the database enforces it)
            getChildFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.chat_host, BoardingChatFragment.newInstance(g.bhId(), g.bhName(), g.memberCount(), g.myRole(), true, true))
                    .commitAllowingStateLoss();
            show(b.chatHost);
        }, e -> {
            if (my != token || b == null) return;
            b.errorText.setText(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.boarding_load_error), e));
            show(b.errorBox);
        });
    }

    /** v2.9.4: the group LIST is showing (not a chat): keep the tab bar. */
    private void setList() {
        if (backCallback != null) backCallback.setEnabled(false);
        Fragment p = getParentFragment();
        if (p instanceof ShellFragment) ((ShellFragment) p).setChatFull(false);
    }

    private static final class BhGroupRole {
        static boolean isOwner(BhGroup g) { return com.bofo.finder.model.GroupMessage.OWNER.equals(g.myRole()); }
    }

    private void removeChat() {
        Fragment f = getChildFragmentManager().findFragmentById(R.id.chat_host);
        if (f != null) getChildFragmentManager().beginTransaction().remove(f).commitAllowingStateLoss();
    }

    private void join() {
        if (joining || b == null) return;
        String code = BhGroupService.normalizeCode(b.code.getText().toString());
        if (code.length() != 8) { error(getString(R.string.boarding_code_short)); return; }
        joining = true;
        b.join.setEnabled(false);
        b.join.setText(R.string.boarding_joining);
        b.joinError.setVisibility(View.GONE);
        Async.run(() -> svc.join(code), (String bhId) -> {
            joining = false;
            if (b == null) return;
            b.join.setEnabled(true);
            b.join.setText(R.string.boarding_join);
            loadGroups();
        }, e -> {
            joining = false;
            if (b == null) return;
            b.join.setEnabled(true);
            b.join.setText(R.string.boarding_join);
            error(joinMessage(e));
        });
    }

    private String joinMessage(Throwable e) {
        if (Connectivity.isNetworkError(e)) return getString(R.string.list_offline);
        String text = String.valueOf(e.getMessage());
        switch (BoardingRules.classifyJoin(text)) {
            case BAD_CODE: return getString(R.string.boarding_err_bad_code);
            case REMOVED: return getString(R.string.boarding_err_removed);
            case OTHER_HOUSE: return getString(R.string.boarding_err_other_house);
            case STUDENTS_ONLY: return getString(R.string.boarding_err_students_only);
            case SUSPENDED: return getString(R.string.boarding_err_suspended);
            default: return getString(R.string.boarding_err_generic);
        }
    }

    private void error(String msg) {
        if (b == null) return;
        b.joinError.setText(msg);
        b.joinError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        Fragment p = getParentFragment();
        if (p instanceof ShellFragment) ((ShellFragment) p).setChatFull(false);          // another tab or screen: the bar is back
        token++;
        b = null;
        super.onDestroyView();
    }
}
