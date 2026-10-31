package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bofo.finder.R;
import com.bofo.finder.databinding.ItemMsgMineBinding;
import com.bofo.finder.databinding.ItemMsgTheirsBinding;
import com.bofo.finder.databinding.SheetBofoBinding;
import com.bofo.finder.service.BofoAiService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.BrowseState;
import com.bofo.finder.util.ChatRules;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Ask BOFO (Edge Function bofo-chat). Offline or refused: the built-in FAQ answers. The mascot thinks while waiting. Conversation is not saved. */
public class BofoPanelSheet extends BottomSheetDialogFragment {

    private SheetBofoBinding b;
    private boolean waiting;
    private int token;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = SheetBofoBinding.inflate(inflater, container, false);
        b.send.setOnClickListener(v -> ask());
        say(getString(R.string.bofo_hello), false);
        return b.getRoot();
    }

    private void ask() {
        if (waiting) return;
        String q = ChatRules.outgoing(b.input.getText().toString(), 300);
        if (q.isEmpty()) return;
        b.input.setText("");
        say(q, true);
        waiting = true;
        b.mascot.setMood(MascotView.Mood.THINKING);
        final int my = ++token;
        String college = BrowseState.collegeId;
        Async.run(() -> new BofoAiService().ask(q, college).reply(), (String reply) -> {
            if (my != token || b == null) return;
            done(reply.isEmpty() ? BofoAiService.faq(q) : reply);
        }, e -> {
            if (my != token || b == null) return;
            done(BofoAiService.faq(q));                                  // offline / rate-limited
        });
    }

    private void done(String text) {
        waiting = false;
        b.mascot.setMood(MascotView.Mood.SITTING);
        say(text, false);
    }

    private void say(String text, boolean mine) {
        LayoutInflater in = LayoutInflater.from(requireContext());
        View root; TextView body;
        if (mine) { ItemMsgMineBinding m = ItemMsgMineBinding.inflate(in, b.chatBox, false); root = m.getRoot(); body = m.body; m.meta.setVisibility(View.GONE); }
        else { ItemMsgTheirsBinding m = ItemMsgTheirsBinding.inflate(in, b.chatBox, false); root = m.getRoot(); body = m.body; m.meta.setVisibility(View.GONE); }
        body.setText(text);
        b.chatBox.addView(root);
        b.scroll.post(() -> { if (b != null) b.scroll.fullScroll(View.FOCUS_DOWN); });
    }

    @Override
    public void onDestroyView() {
        token++;
        b = null;
        super.onDestroyView();
    }
}
