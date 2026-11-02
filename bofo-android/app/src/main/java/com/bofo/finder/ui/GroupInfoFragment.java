package com.bofo.finder.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentGroupInfoBinding;
import com.bofo.finder.databinding.ItemGroupMemberBinding;
import com.bofo.finder.model.BhMember;
import com.bofo.finder.service.BhGroupService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BoardingRules;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * v2.2.0 step 5: the owner's Group info for one house = the invite code (copy / share / new code) and the member list (mute / unmute / remove).
 * Opened from the owner's chat menu (nav {@code groupInfoFragment}, args {@link #ARG_BH} and {@link #ARG_TITLE}). The database refuses anyone but the
 * owner, so a student who somehow gets here only sees the error box.
 */
public class GroupInfoFragment extends Fragment {

    public static final String ARG_BH = "bhId", ARG_TITLE = "title";

    private FragmentGroupInfoBinding b;
    private final BhGroupService svc = new BhGroupService();
    private String bhId = "", title = "";
    private String code;                       // current code, null = none
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentGroupInfoBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        Bundle a = requireArguments();
        bhId = a.getString(ARG_BH, "");
        title = a.getString(ARG_TITLE, "");
        b.house.setText(title);
        b.back.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.copy.setOnClickListener(v -> copyCode());
        b.share.setOnClickListener(v -> shareCode());
        b.newCode.setOnClickListener(v -> { if (code == null) regenerate(); else confirmNewCode(); });
        b.retry.setOnClickListener(v -> loadMembers());
        showCode(null);
        loadCode();
        loadMembers();
    }

    // ---- invite code -------------------------------------------------------------------------------------------

    private void loadCode() {
        final int my = token;
        Async.run(() -> svc.inviteCode(bhId), (String c) -> {
            if (my != token || b == null) return;
            showCode(c);
        }, e -> {
            if (my != token || b == null) return;
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.group_code_failed), e));
        });
    }

    private void showCode(@Nullable String c) {
        code = c == null || c.trim().isEmpty() ? null : c.trim();
        if (code == null) {
            b.code.setText(R.string.group_code_none);
            b.code.setTextSize(14);
            b.code.setLetterSpacing(0f);
            b.code.setTypeface(android.graphics.Typeface.DEFAULT);
            b.copy.setVisibility(View.GONE);
            b.share.setVisibility(View.GONE);
            b.newCode.setText(R.string.group_code_create);
        } else {
            b.code.setText(BoardingRules.formatCode(code));
            b.code.setTextSize(30);
            b.code.setLetterSpacing(0.12f);
            b.code.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            b.copy.setVisibility(View.VISIBLE);
            b.share.setVisibility(View.VISIBLE);
            b.newCode.setText(R.string.group_code_new);
        }
    }

    private void copyCode() {
        if (code == null) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("BOFO invite code", BoardingRules.formatCode(code)));
        ListArea.toast(this, getString(R.string.group_code_copied));
    }

    private void shareCode() {
        if (code == null) return;
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, BoardingRules.shareText(title, code));
        startActivity(Intent.createChooser(send, getString(R.string.group_share_chooser)));
    }

    private void confirmNewCode() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.group_code_new_title)
                .setMessage(R.string.group_code_new_msg)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.group_code_new, (d, w) -> regenerate())
                .show();
    }

    private void regenerate() {
        b.newCode.setEnabled(false);
        final int my = token;
        Async.run(() -> svc.regenerateInviteCode(bhId), (String c) -> {
            if (my != token || b == null) return;
            b.newCode.setEnabled(true);
            showCode(c);
        }, e -> {
            if (my != token || b == null) return;
            b.newCode.setEnabled(true);
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.group_code_failed), e));
        });
    }

    // ---- members -----------------------------------------------------------------------------------------------

    private void loadMembers() {
        final int my = token;
        boolean first = b.members.getChildCount() == 0;
        if (first) b.loading.setVisibility(View.VISIBLE);
        b.errorBox.setVisibility(View.GONE);
        Async.run(() -> BoardingRules.memberOrder(svc.members(bhId)), (List<BhMember> list) -> {
            if (my != token || b == null) return;
            b.loading.setVisibility(View.GONE);
            renderMembers(list);
        }, e -> {
            if (my != token || b == null) return;
            b.loading.setVisibility(View.GONE);
            if (b.members.getChildCount() > 0) { ListArea.toast(this, getString(R.string.group_members_error)); return; }
            b.errorText.setText(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.group_members_error), e));
            b.errorBox.setVisibility(View.VISIBLE);
        });
    }

    private void renderMembers(List<BhMember> list) {
        b.members.removeAllViews();
        int students = BoardingRules.activeStudents(list);
        b.membersCount.setText(getResources().getQuantityString(R.plurals.group_members_count, students, students));
        b.membersEmpty.setVisibility(students == 0 ? View.VISIBLE : View.GONE);
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (BhMember m : list) {
            ItemGroupMemberBinding row = ItemGroupMemberBinding.inflate(inf, b.members, false);
            String name = m.name() == null || m.name().trim().isEmpty() ? getString(R.string.group_member_student) : m.name().trim();
            row.initial.setText(BoardingRules.initial(name));
            row.name.setText(name);
            row.status.setText(statusLine(m));
            boolean can = BoardingRules.canModerate(m);
            row.more.setVisibility(can ? View.VISIBLE : View.INVISIBLE);
            if (can) row.more.setOnClickListener(v -> memberMenu(m, name));
            b.members.addView(row.getRoot());
        }
    }

    private String statusLine(BhMember m) {
        String s = getString(m.isOwner() ? R.string.group_member_owner : R.string.group_member_student);
        if ("removed".equals(m.status())) return s + " - " + getString(R.string.group_member_removed);
        if (m.muted()) return s + " - " + getString(R.string.group_member_muted);
        return s;
    }

    private void memberMenu(BhMember m, String name) {
        String[] items = {getString(m.muted() ? R.string.group_member_unmute : R.string.group_member_mute), getString(R.string.group_member_remove)};
        new MaterialAlertDialogBuilder(requireContext()).setTitle(name).setItems(items, (d, which) -> {
            if (which == 0) change(() -> svc.setMuted(bhId, m.userId(), !m.muted()));
            else confirmRemove(m);
        }).show();
    }

    private void confirmRemove(BhMember m) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.group_member_remove_title)
                .setMessage(R.string.group_member_remove_msg)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.group_member_remove, (d, w) -> change(() -> svc.removeMember(bhId, m.userId())))
                .show();
    }

    private interface Change { void run() throws Exception; }

    /** Runs one moderation call, then reloads the list. */
    private void change(Change c) {
        final int my = token;
        Async.run(() -> { c.run(); return Boolean.TRUE; }, ok -> {
            if (my != token || b == null) return;
            loadMembers();
        }, e -> {
            if (my != token || b == null) return;
            ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.group_member_failed), e));
        });
    }

    @Override
    public void onDestroyView() {
        token++;
        b = null;
        super.onDestroyView();
    }
}
