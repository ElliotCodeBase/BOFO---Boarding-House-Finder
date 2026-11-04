package com.bofo.finder.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.model.AnnounceDraft;
import com.bofo.finder.model.AnnounceError;
import com.bofo.finder.model.AnnounceType;
import com.bofo.finder.model.Profile;
import com.bofo.finder.model.Role;
import com.bofo.finder.service.AnnounceAiService;
import com.bofo.finder.util.AiFlag;
import com.bofo.finder.util.AnnounceDraftState;
import com.bofo.finder.util.AnnounceFormState;
import com.bofo.finder.util.AppPrefs;
import com.bofo.finder.util.BrowseState;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Write for me" sheet: stages A and B (v2.4.2, step 5) and stage C (v2.4.3, step 6).
 * <ul>
 * <li>Stage A: the 26 types ({@link AnnounceType#ALL}) with icon, title, one-line description and an urgency badge, plus a search box.</li>
 * <li>Stage B: one input per field of the chosen type, the options (language, tone, length, emojis, sign-off name) and <b>Write for me</b>.</li>
 * <li>Stage C: Write for me validates, then this sheet calls {@link AnnounceAiService} ONCE per press (the service is created once per sheet and
 *     cancelled in onDestroyView) and shows loading (Cancel allowed), the editable draft (title, message with counter, "check before posting",
 *     degraded note, tip, Try again / Shorter / More friendly / More formal / language chips / Edit details / <b>Use this</b>), or the error.
 *     Every adjustment re-sends the SAME facts ({@link AnnounceDraftState} keeps the Submission).</li>
 * </ul>
 * <b>Use this</b> puts {title, message} in a fragment result (key {@link #RESULT_KEY}) and closes; the host copies them into its form and the
 * owner still presses Post. The AI never posts. The host listens with {@link #listen(Fragment, Listener)} in its onCreate (a fragment result survives
 * rotation, a callback field would not) and opens the sheet with {@link #show(Fragment)}.
 * <p>Logic lives in {@link AnnounceFormState} and {@link AnnounceDraftState} (unit tested); this class only draws it. What the owner types and the
 * draft are never written to preferences or logs; only language / tone / length / emoji are remembered ({@link AppPrefs}). A rotation closes the
 * running request and returns to the details form (the typed details are restored by Android; the draft is not kept).</p>
 */
public class AnnounceAiSheet extends BottomSheetDialogFragment {

    /** The host receives the chosen draft once, when the owner presses Use this. */
    public interface Listener { void onUse(String title, String message); }

    public static final String RESULT_KEY = "announce_ai_use";
    private static final String TAG = "announce_ai_sheet";
    private static final String S_TYPE = "type", S_LANG = "lang", S_TONE = "tone", S_LENGTH = "length";
    private static final int SLOTS = 4;

    /** Opens the sheet as a child of {@code host}. Does nothing when the host is gone, its state is saved, or a sheet is already open. */
    public static void show(Fragment host) {
        if (host == null || !host.isAdded()) return;
        androidx.fragment.app.FragmentManager fm = host.getChildFragmentManager();
        if (fm.isStateSaved() || fm.findFragmentByTag(TAG) != null) return;
        new AnnounceAiSheet().show(fm, TAG);
    }

    /** Call once in the host's onCreate: {@code listener} gets the chosen title and message (while the host is started), also after a rotation. */
    public static void listen(Fragment host, Listener listener) {
        host.getChildFragmentManager().setFragmentResultListener(RESULT_KEY, host, (key, b) -> {
            String title = b.getString("title", ""), message = b.getString("message", "");
            if (listener != null) listener.onUse(title, message);
        });
    }

    private AnnounceFormState state = new AnnounceFormState();
    private Bundle saved;                                   // what the system gave back after a rotation (selected chips)
    private boolean blocked;

    // stage C
    private AnnounceDraftState draftState;                  // kept while the owner visits the form again, so edits survive until a regenerate is confirmed
    private boolean resultShown;                            // stage C is on screen
    private AnnounceAiService service;                      // created once per sheet, cancelled in onDestroyView
    private AnnounceAiService.Call call;
    private boolean binding;                                // the draft fields are being filled by code: not a manual edit
    private android.app.Dialog confirm;
    private View stageResult, resLoading, resError, resDraft, resErrorNote;
    private TextView resErrorText, resCounter, resNotes, resDegraded;
    private TextInputLayout resMessageLayout;
    private TextInputEditText resTitle, resMessage;
    private MaterialButton resRetry, resAgain, resShorter, resFriendlier, resFormal;
    private ChipGroup resLangGroup;
    private final Runnable tick = this::onTick;

    private View root, stageList, stageForm, backBtn, emptyView;
    private TextView titleView, typeDesc;
    private RecyclerView list;
    private TypeAdapter adapter;
    private TextInputEditText search, closingName;
    private TextInputLayout closingLayout;
    private MaterialSwitch emojiSwitch, closingSwitch;
    private ChipGroup langGroup, toneGroup, lengthGroup;
    private final TextInputLayout[] fieldLayouts = new TextInputLayout[SLOTS];
    private final TextInputEditText[] fieldInputs = new TextInputEditText[SLOTS];

    @Override public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        saved = s;
        state = AnnounceFormState.restore(s == null ? null : s.getString(S_TYPE));
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle s) {
        Dialog d = super.onCreateDialog(s);
        if (d instanceof ComponentDialog) {
            // Back on the form returns to the list; only Back on the list closes the sheet
            ((ComponentDialog) d).getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override public void handleOnBackPressed() { if (!goBack()) dismissAllowingStateLoss(); }
            });
        }
        return d;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        root = inflater.inflate(R.layout.sheet_announce_ai, container, false);
        stageList = root.findViewById(R.id.ann_stage_list);
        stageForm = root.findViewById(R.id.ann_stage_form);
        backBtn = root.findViewById(R.id.ann_back);
        titleView = root.findViewById(R.id.ann_title);
        typeDesc = root.findViewById(R.id.ann_type_desc);
        emptyView = root.findViewById(R.id.ann_empty);
        list = root.findViewById(R.id.ann_list);
        search = root.findViewById(R.id.ann_search);
        closingName = root.findViewById(R.id.ann_closing_name);
        closingLayout = root.findViewById(R.id.ann_closing_layout);
        emojiSwitch = root.findViewById(R.id.ann_emoji);
        closingSwitch = root.findViewById(R.id.ann_closing);
        langGroup = root.findViewById(R.id.ann_lang_group);
        toneGroup = root.findViewById(R.id.ann_tone_group);
        lengthGroup = root.findViewById(R.id.ann_length_group);
        stageResult = root.findViewById(R.id.ann_stage_result);
        resLoading = root.findViewById(R.id.ann_res_loading);
        resError = root.findViewById(R.id.ann_res_error);
        resDraft = root.findViewById(R.id.ann_res_draft);
        resErrorNote = root.findViewById(R.id.ann_res_error_note);
        resErrorText = root.findViewById(R.id.ann_res_error_text);
        resCounter = root.findViewById(R.id.ann_res_counter);
        resNotes = root.findViewById(R.id.ann_res_notes);
        resDegraded = root.findViewById(R.id.ann_res_degraded);
        resMessageLayout = root.findViewById(R.id.ann_res_message_layout);
        resTitle = root.findViewById(R.id.ann_res_title);
        resMessage = root.findViewById(R.id.ann_res_message);
        resRetry = root.findViewById(R.id.ann_res_retry);
        resAgain = root.findViewById(R.id.ann_res_again);
        resShorter = root.findViewById(R.id.ann_res_shorter);
        resFriendlier = root.findViewById(R.id.ann_res_friendlier);
        resFormal = root.findViewById(R.id.ann_res_formal);
        resLangGroup = root.findViewById(R.id.ann_res_lang_group);
        int[] layoutIds = {R.id.ann_field_1, R.id.ann_field_2, R.id.ann_field_3, R.id.ann_field_4};
        int[] inputIds = {R.id.ann_input_1, R.id.ann_input_2, R.id.ann_input_3, R.id.ann_input_4};
        for (int i = 0; i < SLOTS; i++) {
            fieldLayouts[i] = root.findViewById(layoutIds[i]);
            fieldInputs[i] = root.findViewById(inputIds[i]);
            final TextInputLayout tl = fieldLayouts[i];
            fieldInputs[i].addTextChangedListener(new SimpleWatcher() { @Override public void afterTextChanged(Editable e) { tl.setError(null); } });
        }

        // AI switched off by the admin (admins themselves are exempt, like on the server): say so and close at once
        Profile me = BrowseState.me;
        boolean admin = me != null && me.role() == Role.ADMIN;
        if (!AiFlag.on() && !admin) {
            blocked = true;
            Toast.makeText(requireContext(), R.string.announce_err_ai_disabled, Toast.LENGTH_LONG).show();
            root.post(this::dismissAllowingStateLoss);
            return root;
        }

        root.findViewById(R.id.ann_close).setOnClickListener(v -> dismissAllowingStateLoss());
        backBtn.setOnClickListener(v -> goBack());
        root.findViewById(R.id.ann_write).setOnClickListener(v -> write());
        root.findViewById(R.id.ann_res_cancel).setOnClickListener(v -> cancelLoading());
        root.findViewById(R.id.ann_res_error_details).setOnClickListener(v -> backToForm());
        root.findViewById(R.id.ann_res_details).setOnClickListener(v -> backToForm());
        root.findViewById(R.id.ann_use).setOnClickListener(v -> useThis());
        resRetry.setOnClickListener(v -> withConfirm(() -> run(draftState == null ? null : draftState.retry())));
        resAgain.setOnClickListener(v -> adjust(AnnounceType.Adjust.AGAIN));
        resShorter.setOnClickListener(v -> adjust(AnnounceType.Adjust.SHORTER));
        resFriendlier.setOnClickListener(v -> adjust(AnnounceType.Adjust.FRIENDLIER));
        resFormal.setOnClickListener(v -> adjust(AnnounceType.Adjust.FORMAL));
        SimpleWatcher draftWatcher = new SimpleWatcher() {
            @Override public void afterTextChanged(Editable e) { onDraftEdited(); }
        };
        resTitle.addTextChangedListener(draftWatcher);
        resMessage.addTextChangedListener(draftWatcher);

        adapter = new TypeAdapter(this::pick);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        search.addTextChangedListener(new SimpleWatcher() { @Override public void afterTextChanged(Editable e) { filterList(); } });
        filterList();

        buildChips();
        langGroup.setSaveEnabled(false); toneGroup.setSaveEnabled(false); lengthGroup.setSaveEnabled(false); resLangGroup.setSaveEnabled(false);
        closingSwitch.setOnCheckedChangeListener((b, on) -> closingLayout.setVisibility(on ? View.VISIBLE : View.GONE));

        if (state.type() != null) bindType(state.type(), false);
        render();
        return root;
    }

    @Override public void onDestroyView() {
        if (root != null) root.removeCallbacks(tick);
        cancelCall();                                                    // a late result after the sheet closed is ignored
        if (confirm != null) { confirm.dismiss(); confirm = null; }
        draftState = null; service = null; resultShown = false;
        super.onDestroyView();
    }

    @Override public void onStart() {
        super.onStart();
        if (blocked || getDialog() == null) return;
        // tall sheet so the list / form can scroll inside it; the keyboard must not hide the Write button
        FrameLayout sheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.getLayoutParams().height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.92f);
            sheet.requestLayout();
            BottomSheetBehavior<FrameLayout> bh = BottomSheetBehavior.from(sheet);
            bh.setSkipCollapsed(true);
            bh.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        if (getDialog().getWindow() != null) getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (blocked || langGroup == null) return;
        out.putString(S_TYPE, state.typeIdToSave());
        AnnounceType.Lang l = selectedLang(); if (l != null) out.putString(S_LANG, l.wire);
        AnnounceType.Tone t = selectedTone(); if (t != null) out.putString(S_TONE, t.wire);
        AnnounceType.Length n = selectedLength(); if (n != null) out.putString(S_LENGTH, n.wire);
    }

    /* ---------- stages ---------- */

    private void pick(AnnounceType t) {
        hideKeyboard();
        state.pick(t);
        bindType(t, true);
        render();
    }

    private boolean goBack() {
        hideKeyboard();
        if (resultShown) {                                              // stage C: Back = Cancel while writing, else Edit details
            if (draftState != null && draftState.loading()) cancelLoading(); else backToForm();
            return true;
        }
        boolean handled = state.back();
        if (handled) render();
        return handled;
    }

    private void render() {
        boolean result = resultShown && draftState != null;
        boolean form = !result && state.stage() == AnnounceFormState.Stage.DETAILS;
        stageList.setVisibility(!result && !form ? View.VISIBLE : View.GONE);
        stageForm.setVisibility(form ? View.VISIBLE : View.GONE);
        stageResult.setVisibility(result ? View.VISIBLE : View.GONE);
        backBtn.setVisibility(form || result ? View.VISIBLE : View.GONE);
        backBtn.setContentDescription(getString(result ? R.string.announce_back_details_cd : R.string.announce_back_cd));
        titleView.setText(result ? getString(R.string.announce_result_title)
                : form ? res(state.type().titleName(), state.type().id) : getString(R.string.announce_pick_type_title));
        if (result) renderResult();
    }

    private void filterList() {
        List<AnnounceType> shown = AnnounceFormState.filter(AnnounceType.ALL, search.getText() == null ? "" : search.getText().toString(),
                t -> res(t.titleName(), t.id));
        adapter.submit(shown);
        emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Fills stage B for a type. {@code fresh} = the owner just picked it: empty inputs, remembered options. After a rotation the system restores the texts. */
    private void bindType(AnnounceType t, boolean fresh) {
        typeDesc.setText(res(t.descName(), ""));
        for (int i = 0; i < SLOTS; i++) {
            if (i >= t.fields.size()) { fieldLayouts[i].setVisibility(View.GONE); continue; }
            AnnounceType.Field f = t.fields.get(i);
            String label = res(f.labelName(), f.key);
            fieldLayouts[i].setVisibility(View.VISIBLE);
            fieldLayouts[i].setHint(f.required ? getString(R.string.announce_required_label, label) : label);
            fieldLayouts[i].setPlaceholderText(res(t.hintName(f.key), ""));
            fieldLayouts[i].setError(null);
            if (fresh) fieldInputs[i].setText("");
        }
        Context c = requireContext();
        AnnounceFormState.Options o = AnnounceFormState.defaults(t, AppPrefs.announceLang(c), AppPrefs.announceTone(c), AppPrefs.announceLength(c), AppPrefs.announceEmoji(c));
        AnnounceType.Lang lang = o.lang; AnnounceType.Tone tone = o.tone; AnnounceType.Length length = o.length;
        if (!fresh && saved != null) {                                    // rotation: keep what the owner had selected
            lang = AnnounceType.Lang.fromWire(saved.getString(S_LANG), lang);
            tone = AnnounceType.Tone.fromWire(saved.getString(S_TONE), tone);
            length = AnnounceType.Length.fromWire(saved.getString(S_LENGTH), length);
        }
        check(langGroup, lang); check(toneGroup, tone); check(lengthGroup, length);
        if (fresh) {
            emojiSwitch.setChecked(o.emoji);
            Profile me = BrowseState.me;
            String name = AnnounceFormState.closingDefault(me == null ? null : me.fullName());
            closingName.setText(name);
            closingSwitch.setChecked(!name.isEmpty());                   // sign-off with the owner's own name, on when a name is known; the owner can switch it off
        }
        closingLayout.setVisibility(closingSwitch.isChecked() ? View.VISIBLE : View.GONE);
    }

    /* ---------- Write for me ---------- */

    private void write() {
        AnnounceType t = state.type();
        if (t == null) return;
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < t.fields.size(); i++) {
            CharSequence v = fieldInputs[i].getText();
            fields.put(t.fields.get(i).key, v == null ? "" : v.toString());
        }
        AnnounceType.Lang lang = selectedLang(); AnnounceType.Tone tone = selectedTone(); AnnounceType.Length length = selectedLength();
        boolean emoji = emojiSwitch.isChecked(), closingOn = closingSwitch.isChecked();
        String name = closingName.getText() == null ? "" : closingName.getText().toString();
        AnnounceFormState.Check c = AnnounceFormState.check(t, lang, tone, length, emoji, closingOn, name, fields);
        if (!c.ok()) { showProblem(t, c.problem); return; }

        Context ctx = requireContext();                                   // only these four choices are remembered, never the typed details
        AppPrefs.setAnnounceLang(ctx, c.submission.lang.wire);
        AppPrefs.setAnnounceTone(ctx, AnnounceFormState.toneToRemember(t, c.submission.tone));
        AppPrefs.setAnnounceLength(ctx, c.submission.length.wire);
        AppPrefs.setAnnounceEmoji(ctx, c.submission.emoji);

        hideKeyboard();
        final AnnounceFormState.Submission sub = c.submission;
        if (draftState != null && draftState.needsConfirmBeforeRegenerate()) confirmRegenerate(() -> startGeneration(sub), null);
        else startGeneration(sub);
    }

    private void showProblem(AnnounceType t, AnnounceFormState.ProblemText p) {
        List<String> labels = new ArrayList<>();
        for (String k : p.fieldKeys) { AnnounceType.Field f = t.field(k); labels.add(f == null ? k : res(f.labelName(), k)); }
        int resId = getResources().getIdentifier(p.stringName, "string", requireContext().getPackageName());
        String msg = resId == 0 ? getString(R.string.announce_err_invalid) : (p.stringName.equals("announce_err_invalid") ? getString(resId) : getString(resId, join(labels)));
        int slot = -1;
        for (int i = 0; i < t.fields.size(); i++) if (t.fields.get(i).key.equals(p.focusKey())) slot = i;
        if (slot < 0) { Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show(); return; }
        fieldLayouts[slot].setError(msg);
        fieldInputs[slot].requestFocus();
    }

    /* ---------- stage C: generate, review, use ---------- */

    /** Write for me pressed with valid details: a new draft state (the facts are kept in it) and ONE request. */
    private void startGeneration(AnnounceFormState.Submission sub) {
        draftState = new AnnounceDraftState(sub);
        resultShown = true;
        run(draftState.first());
    }

    /** Sends one request for the facts of the current state (null = not allowed now, e.g. a double tap while loading). */
    private void run(AnnounceDraftState.Request r) {
        if (r == null || draftState == null || getView() == null) return;
        hideKeyboard();
        if (service == null) service = new AnnounceAiService();
        AnnounceFormState.Submission s = draftState.submission();
        resultShown = true;
        render();                                                          // loading
        call = service.generate(s.type, r.lang, r.tone, r.length, s.emoji, s.closingOn, s.closingName, s.fields, r.adjust, this::onGenerated);
    }

    private void onGenerated(AnnounceAiService.Result r) {
        if (draftState == null || getView() == null) return;
        if (!draftState.onResult(r.draft, r.error, r.retryAfterSeconds, r.field, r.problem)) return;
        call = null;
        if (draftState.phase() == AnnounceDraftState.Phase.SUCCESS) {
            bindDraft(draftState.draft());
        } else if (draftState.route() == AnnounceDraftState.Route.BACK_TO_FORM) {
            AnnounceFormState.ProblemText p = draftState.formProblem();
            backToForm();
            if (p != null && state.type() != null) showProblem(state.type(), p);       // field marked + focused on the form
            return;
        }
        render();
    }

    /** Cancel while writing: back to the previous draft when there was one, else to the form. */
    private void cancelLoading() {
        if (draftState == null) return;
        cancelCall();
        if (draftState.cancel() == AnnounceDraftState.Phase.SUCCESS) render(); else backToForm();
    }

    /** Edit details: the form again with every value kept; the language / tone / length chips show what the last request used. */
    private void backToForm() {
        if (draftState != null) {
            cancelCall();
            check(langGroup, draftState.lang()); check(toneGroup, draftState.tone()); check(lengthGroup, draftState.length());
        }
        resultShown = false;
        if (root != null) root.removeCallbacks(tick);
        render();
    }

    private void cancelCall() {
        if (call != null) call.cancel();
        if (service != null) service.cancelAll();
        call = null;
    }

    private void renderResult() {
        AnnounceDraftState.Phase ph = draftState.phase();
        resLoading.setVisibility(ph == AnnounceDraftState.Phase.LOADING ? View.VISIBLE : View.GONE);
        resError.setVisibility(ph == AnnounceDraftState.Phase.ERROR ? View.VISIBLE : View.GONE);
        resDraft.setVisibility(ph == AnnounceDraftState.Phase.SUCCESS ? View.VISIBLE : View.GONE);
        root.removeCallbacks(tick);
        boolean idle = ph == AnnounceDraftState.Phase.SUCCESS;           // the adjust buttons are off while writing (they are also hidden then)
        resAgain.setEnabled(idle); resShorter.setEnabled(idle); resFriendlier.setEnabled(idle); resFormal.setEnabled(idle);
        if (ph == AnnounceDraftState.Phase.ERROR) {
            AnnounceError e = draftState.error();
            String msg;
            if (e == AnnounceError.RATE_LIMITED) {
                int left = draftState.secondsLeft();
                msg = left > 0 ? getString(R.string.announce_err_rate_limited, left) : getString(R.string.announce_err_rate_ready);
            } else {
                msg = res(e.stringName(), getString(R.string.announce_err_generic));
            }
            resErrorText.setText(msg);
            resErrorNote.setVisibility(draftState.errorCanRetry() ? View.GONE : View.VISIBLE);   // no retry: the owner can still write it
            resRetry.setVisibility(draftState.errorCanRetry() ? View.VISIBLE : View.GONE);
            resRetry.setEnabled(draftState.canRetryNow());
            if (draftState.secondsLeft() > 0) root.postDelayed(tick, 1000);
        } else if (ph == AnnounceDraftState.Phase.SUCCESS) {
            AnnounceDraft d = draftState.draft();
            resDegraded.setVisibility(d.degraded ? View.VISIBLE : View.GONE);
            boolean tip = d.notes != null && !d.notes.isEmpty();
            resNotes.setVisibility(tip ? View.VISIBLE : View.GONE);
            if (tip) resNotes.setText(getString(R.string.announce_notes_line, getString(R.string.announce_notes_label), d.notes));
            syncLangChips();
            updateCounter();
        }
    }

    /** One second of the RATE_LIMITED wait. */
    private void onTick() {
        if (draftState == null || getView() == null || !resultShown) return;
        draftState.tick();
        renderResult();                                                    // re-posts itself while seconds are left
    }

    /** Puts a fresh draft in the two fields (this is not a manual edit). */
    private void bindDraft(AnnounceDraft d) {
        binding = true;
        try {
            resTitle.setText(d.title);
            resMessage.setText(d.message);
            resTitle.setError(null);
            resMessageLayout.setError(null);
        } finally { binding = false; }
        updateCounter();
    }

    private void onDraftEdited() {
        if (binding || draftState == null) return;
        draftState.setEdited(text(resTitle), text(resMessage));
        resMessageLayout.setError(null);
        updateCounter();
    }

    private void updateCounter() {
        resCounter.setText(getString(R.string.announce_draft_counter, text(resMessage).length(), AnnounceDraft.MAX_MESSAGE));
    }

    private static String text(TextInputEditText e) { return e.getText() == null ? "" : e.getText().toString(); }

    private void adjust(AnnounceType.Adjust a) {
        withConfirm(() -> run(draftState == null ? null : draftState.adjust(a)));
    }

    private void onLangChip(AnnounceType.Lang l) {
        if (draftState == null || l == draftState.lang()) { syncLangChips(); return; }
        withConfirm(() -> run(draftState == null ? null : draftState.changeLang(l)));
    }

    private void syncLangChips() {
        if (draftState != null) check(resLangGroup, draftState.lang());
    }

    /** Runs {@code go} at once, or after the owner agrees to lose the changes they made to the draft by hand. */
    private void withConfirm(Runnable go) {
        if (draftState != null && draftState.needsConfirmBeforeRegenerate()) confirmRegenerate(go, this::syncLangChips);
        else go.run();
    }

    private void confirmRegenerate(Runnable yes, Runnable no) {
        if (confirm != null) confirm.dismiss();
        confirm = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.announce_regen_title)
                .setMessage(R.string.announce_regen_body)
                .setPositiveButton(R.string.announce_regen_yes, (d, w) -> yes.run())
                .setNegativeButton(R.string.announce_regen_no, (d, w) -> { if (no != null) no.run(); })
                .setOnCancelListener(d -> { if (no != null) no.run(); })
                .show();
    }

    /** Use this: hands {title, message} to the host and closes. The host fills its form; it never posts. */
    private void useThis() {
        if (draftState == null || draftState.phase() != AnnounceDraftState.Phase.SUCCESS) return;
        AnnounceDraftState.Use u = AnnounceDraftState.prepareUse(text(resTitle), text(resMessage));
        if (u == null) { resMessageLayout.setError(getString(R.string.announce_use_empty)); return; }
        hideKeyboard();
        Bundle b = new Bundle();
        b.putString("title", u.title);
        b.putString("message", u.message);
        getParentFragmentManager().setFragmentResult(RESULT_KEY, b);
        dismissAllowingStateLoss();
    }

    /* ---------- option chips ---------- */

    private void buildChips() {
        for (AnnounceType.Lang v : AnnounceType.Lang.values()) addChip(langGroup, v, "announce_lang_" + v.wire);
        for (AnnounceType.Tone v : AnnounceType.Tone.values()) addChip(toneGroup, v, "announce_tone_" + v.wire);
        for (AnnounceType.Length v : AnnounceType.Length.values()) addChip(lengthGroup, v, "announce_length_" + v.wire);
        for (AnnounceType.Lang v : AnnounceType.Lang.values()) addChip(resLangGroup, v, "announce_lang_" + v.wire);
        for (int i = 0; i < resLangGroup.getChildCount(); i++) {          // stage C: tapping another language regenerates (after a confirm if the draft was edited)
            View c = resLangGroup.getChildAt(i);
            c.setOnClickListener(v -> { if (v.getTag() instanceof AnnounceType.Lang) onLangChip((AnnounceType.Lang) v.getTag()); });
        }
    }

    private void addChip(ChipGroup g, Object value, String stringName) {
        Chip c = new Chip(requireContext());
        c.setId(View.generateViewId());
        c.setTag(value);
        c.setText(res(stringName, String.valueOf(value)));
        c.setCheckable(true);
        c.setChipMinHeight(48 * getResources().getDisplayMetrics().density);
        c.setSaveEnabled(false);                                          // generated ids are not stable: never restore a checked state by id (selection is saved by hand)
        g.addView(c);
    }

    private static void check(ChipGroup g, Object value) {
        for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i).getTag() == value) { g.check(g.getChildAt(i).getId()); return; }
    }

    private static Object selected(ChipGroup g) {
        int id = g.getCheckedChipId();
        View v = id == View.NO_ID ? null : g.findViewById(id);
        return v == null ? null : v.getTag();
    }

    private AnnounceType.Lang selectedLang() { Object o = selected(langGroup); return o instanceof AnnounceType.Lang ? (AnnounceType.Lang) o : null; }
    private AnnounceType.Tone selectedTone() { Object o = selected(toneGroup); return o instanceof AnnounceType.Tone ? (AnnounceType.Tone) o : null; }
    private AnnounceType.Length selectedLength() { Object o = selected(lengthGroup); return o instanceof AnnounceType.Length ? (AnnounceType.Length) o : null; }

    /* ---------- helpers ---------- */

    /** String by resource NAME (the catalogue is data-driven); falls back to {@code fallback} when the name is missing, never throws. */
    private String res(String name, String fallback) {
        try {
            int id = getResources().getIdentifier(name, "string", requireContext().getPackageName());
            return id == 0 ? fallback : getString(id);
        } catch (Exception e) { return fallback; }
    }

    private int drawable(String name) {
        int id = getResources().getIdentifier(name, "drawable", requireContext().getPackageName());
        return id == 0 ? R.drawable.ic_sparkle : id;
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(", "); b.append(s); }
        return b.toString();
    }

    private void hideKeyboard() {
        View v = getView();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
    }

    /* ---------- stage A list ---------- */

    private interface OnPick { void pick(AnnounceType t); }

    private final class TypeAdapter extends RecyclerView.Adapter<TypeAdapter.Holder> {
        private final OnPick onPick;
        private List<AnnounceType> items = AnnounceType.ALL;
        TypeAdapter(OnPick onPick) { this.onPick = onPick; }

        void submit(List<AnnounceType> l) { items = l; notifyDataSetChanged(); }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
            return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_announce_type, p, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            final AnnounceType t = items.get(position);
            String title = res(t.titleName(), t.id), desc = res(t.descName(), "");
            h.icon.setImageResource(drawable(t.icon));
            h.title.setText(title);
            h.desc.setText(desc);
            h.desc.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);
            String badgeName = AnnounceFormState.badgeStringName(t);
            Context c = h.itemView.getContext();
            if (badgeName == null) {
                h.badge.setVisibility(View.GONE);
                h.itemView.setContentDescription(getString(R.string.announce_type_row_cd, title, desc));
            } else {
                String badge = res(badgeName, "");
                boolean urgent = t.isUrgent();
                h.badge.setText(badge);
                h.badge.setBackgroundResource(urgent ? R.drawable.bg_ann_badge_urgent : R.drawable.bg_ann_badge_important);
                h.badge.setTextColor(ContextCompat.getColor(c, urgent ? R.color.bofo_error_deep : R.color.bofo_text_2));
                h.badge.setVisibility(View.VISIBLE);
                h.itemView.setContentDescription(getString(R.string.announce_type_row_urgency_cd, title, badge, desc));
            }
            h.itemView.setOnClickListener(v -> onPick.pick(t));
        }

        @Override public int getItemCount() { return items.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon; final TextView title, desc, badge;
            Holder(@NonNull View v) {
                super(v);
                icon = v.findViewById(R.id.ann_row_icon); title = v.findViewById(R.id.ann_row_title);
                desc = v.findViewById(R.id.ann_row_desc); badge = v.findViewById(R.id.ann_row_badge);
            }
        }
    }
}
