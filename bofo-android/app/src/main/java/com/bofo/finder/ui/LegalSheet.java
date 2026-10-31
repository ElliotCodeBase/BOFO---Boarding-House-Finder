package com.bofo.finder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.bofo.finder.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Bottom sheet that shows one agreement (terms, privacy or owner). */
public class LegalSheet extends BottomSheetDialogFragment {
    private static final String ARG = "doc";

    public static void show(FragmentManager fm, LegalText.Doc doc) {
        LegalSheet s = new LegalSheet();
        Bundle b = new Bundle();
        b.putString(ARG, doc.name());
        s.setArguments(b);
        s.show(fm, "legal");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.sheet_legal, container, false);
        LegalText.Doc doc = LegalText.Doc.valueOf(requireArguments().getString(ARG, LegalText.Doc.TERMS.name()));
        ((TextView) v.findViewById(R.id.legal_title)).setText(LegalText.title(doc));
        ((TextView) v.findViewById(R.id.legal_body)).setText(LegalText.body(doc));
        ((Button) v.findViewById(R.id.legal_close)).setOnClickListener(x -> dismiss());
        return v;
    }
}
