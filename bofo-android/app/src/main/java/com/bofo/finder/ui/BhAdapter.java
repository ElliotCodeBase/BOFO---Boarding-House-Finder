package com.bofo.finder.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bofo.finder.R;
import com.bofo.finder.databinding.ItemBhCardBinding;
import com.bofo.finder.databinding.ItemBhSkeletonBinding;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.util.Geo;
import com.bofo.finder.util.ListRules;
import com.bofo.finder.util.Money;
import com.bumptech.glide.Glide;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Boarding-house cards in a grid (Home, Explore, Saved). Heart state comes from {@link #setFavorites}. */
public final class BhAdapter extends ListAdapter<BoardingHouse, BhAdapter.VH> {

    public interface Listener {
        void open(BoardingHouse b);
        void heart(BoardingHouse b);
    }

    private static final DiffUtil.ItemCallback<BoardingHouse> DIFF = new DiffUtil.ItemCallback<BoardingHouse>() {
        @Override public boolean areItemsTheSame(@NonNull BoardingHouse a, @NonNull BoardingHouse b) { return a.id().equals(b.id()); }
        @Override public boolean areContentsTheSame(@NonNull BoardingHouse a, @NonNull BoardingHouse b) { return a.equals(b); }
    };
    private static final String PAYLOAD_HEART = "heart";

    private final Listener listener;
    private Set<String> favs = Collections.emptySet();
    private boolean showDistance;

    public BhAdapter(Listener listener) { super(DIFF); this.listener = listener; }

    public void setFavorites(Set<String> ids) {
        favs = ids == null ? Collections.<String>emptySet() : ids;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_HEART);
    }

    /** Distances are only meaningful when a campus is the reference point. */
    public void setShowDistance(boolean on) {
        if (showDistance == on) return;
        showDistance = on;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemBhCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        BoardingHouse b = getItem(position);
        ItemBhCardBinding v = h.b;
        v.name.setText(b.name());
        String where = ListRules.locationText(b.address());                    // v2.0.99: place under the name
        v.location.setText(where);
        v.location.setVisibility(where.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        v.price.setText(Money.perMonth(b.price()));
        v.rating.setText(ListRules.ratingText(b.avgRating(), b.reviewCount()));
        v.distance.setText(showDistance ? Geo.fmt(b.distanceKm()) : "");
        v.beds.setText(ListRules.bedsText(b.availableBeds()));
        String free = ListRules.freeText(b.water(), b.electricity(), b.wifi());
        v.free.setText(free);
        v.free.setVisibility(free.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        String gp = com.bofo.finder.util.GenderRules.normalize(b.genderPolicy());      // v2.0.87: who can stay (hidden when the database has no gender_policy yet)
        v.gender.setText(gp.isEmpty() ? "" : v.getRoot().getContext().getString(com.bofo.finder.util.GenderRules.labelRes(gp)));
        v.gender.setVisibility(gp.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        v.verified.setImageResource(b.ownerVerified() ? R.drawable.ic_shield_verified : R.drawable.ic_shield_unverified);      // v2.0.90: green shield = verified, grey shield = not verified
        v.verifiedText.setText(b.ownerVerified() ? R.string.detail_verified : R.string.detail_not_verified);
        v.verifiedText.setTextColor(androidx.core.content.ContextCompat.getColor(v.getRoot().getContext(), b.ownerVerified() ? R.color.bofo_success : R.color.bofo_text_2));
        v.badge.setVisibility(b.featured() ? android.view.View.VISIBLE : android.view.View.GONE);
        String url = b.photoUrl();
        Glide.with(v.photo).load(url == null || url.isEmpty() ? (String) null : url).centerCrop().placeholder(R.color.bofo_skeleton).into(v.photo);
        bindHeart(v, b);
        v.getRoot().setOnClickListener(x -> listener.open(b));
        v.heart.setOnClickListener(x -> listener.heart(b));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) { super.onBindViewHolder(h, position, payloads); return; }
        bindHeart(h.b, getItem(position));      // only the heart changed: do not reload the photo
    }

    private void bindHeart(ItemBhCardBinding v, BoardingHouse b) {
        boolean on = favs.contains(b.id());
        v.heart.setImageResource(on ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }

    public static final class VH extends RecyclerView.ViewHolder {
        final ItemBhCardBinding b;
        VH(ItemBhCardBinding b) { super(b.getRoot()); this.b = b; }
    }

    /** Grey placeholder cards while the first load runs. */
    public static final class Skeleton extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(ItemBhSkeletonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false).getRoot()) { };
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) { }
        @Override public int getItemCount() { return 6; }
    }
}
