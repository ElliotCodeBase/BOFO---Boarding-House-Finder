package com.bofo.finder.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bofo.finder.R;
import com.bofo.finder.databinding.FragmentBhDetailBinding;
import com.bofo.finder.databinding.ItemReviewBinding;
import com.bofo.finder.model.Amenity;
import com.bofo.finder.model.BoardingHouse;
import com.bofo.finder.service.AuthService;
import com.bofo.finder.service.BhService;
import com.bofo.finder.service.ChatService;
import com.bofo.finder.util.Async;
import com.bofo.finder.util.Connectivity;
import com.bofo.finder.util.ErrorText;
import com.bofo.finder.util.Favorites;
import com.bofo.finder.util.LocationHelper;
import com.bofo.finder.util.ListRules;
import com.bofo.finder.util.Money;
import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One boarding house: photo pager, price, rating, amenities, contact, reviews. "Chat with owner" opens the chat (Stage 7). */
public class BhDetailFragment extends Fragment {

    public static final String ARG_ID = "id";
    private static final int MAX_REVIEWS = 30;

    /** Opens the detail screen on top of the shell (Back returns to the same tab). */
    public static void open(Fragment from, String id) {
        if (from.getContext() != null) com.bofo.finder.util.AppPrefs.addViewed(from.requireContext(), id);      // v2.0.81: the map dims pins of places already seen
        Bundle a = new Bundle();
        a.putString(ARG_ID, id);
        NavHostFragment.findNavController(from).navigate(R.id.bhDetailFragment, a);
    }

    private FragmentBhDetailBinding b;
    private String id;
    private int token;
    private LocationHelper location;      // Stage 6: for the Directions card (registers the permission launcher, so it is made in onCreate)

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        location = new LocationHelper(this);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentBhDetailBinding.inflate(inflater, container, false);
        id = requireArguments().getString(ARG_ID, "");
        View.OnClickListener back = v -> NavHostFragment.findNavController(this).navigateUp();
        b.back.setOnClickListener(back);
        b.errorBack.setOnClickListener(back);
        b.retry.setOnClickListener(v -> load());
        b.heart.setOnClickListener(v -> Favorites.toggle(id, msg -> ListArea.toast(this, msg)));
        Favorites.load(null);
        Favorites.IDS.observe(getViewLifecycleOwner(), ids ->
                b.heart.setImageResource(ids != null && ids.contains(id) ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline));
        load();
        return b.getRoot();
    }

    private void load() {
        final int my = ++token;
        b.loading.setVisibility(View.VISIBLE);
        b.errorBox.setVisibility(View.GONE);
        b.content.setVisibility(View.GONE);
        Async.run(() -> {
            BhService s = new BhService();
            JSONObject d = s.detail(id);
            JSONArray rv;
            try { rv = s.reviews(id); } catch (Exception e) { rv = new JSONArray(); }      // reviews are optional: the place must still open
            return new Object[]{d, rv};
        }, (Object[] r) -> {
            if (my != token || b == null) return;
            try { show((JSONObject) r[0], (JSONArray) r[1]); }
            catch (Exception e) { fail(my, e); }
        }, e -> fail(my, e));
    }

    private void fail(int my, Throwable e) {
        if (my != token || b == null) return;
        b.loading.setVisibility(View.GONE);
        b.content.setVisibility(View.GONE);
        b.chatBar.setVisibility(View.GONE);
        b.errorText.setText(Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.detail_error), e));
        b.errorBox.setVisibility(View.VISIBLE);
    }

    private void show(JSONObject d, JSONArray reviews) throws Exception {
        BoardingHouse bh = BoardingHouse.from(d);
        b.name.setText(bh.name());
        b.price.setText(getString(R.string.detail_per_month, Money.peso(bh.price()), "per_room".equals(d.optString("price_mode", "per_bed")) ? "room" : "bed"));
        b.infoChips.removeAllViews();
        String gp = com.bofo.finder.util.GenderRules.normalize(bh.genderPolicy());      // v2.0.87
        if (!gp.isEmpty()) b.infoChips.addView(Chips.create(b.infoChips, getString(com.bofo.finder.util.GenderRules.labelRes(gp)), false, false));
        if (!bh.ownerVerified()) b.infoChips.addView(Chips.create(b.infoChips, getString(R.string.detail_not_verified), false, false));     // v2.0.89
        if (bh.ownerVerified()) b.infoChips.addView(Chips.create(b.infoChips, getString(R.string.detail_verified), false, false));
        int free = bh.availableBeds();
        b.infoChips.addView(Chips.create(b.infoChips, free == 1 ? getString(R.string.detail_bed_available) : getString(R.string.detail_beds_available, free), false, false));
        if (bh.distanceKm() > 0) b.infoChips.addView(Chips.create(b.infoChips, getString(R.string.detail_km_away, String.format(java.util.Locale.US, "%.1f", bh.distanceKm())), false, false));
        b.rating.setText(ListRules.ratingText(bh.avgRating(), bh.reviewCount()));
        b.address.setText(bh.address());
        b.address.setVisibility(bh.address().isEmpty() ? View.GONE : View.VISIBLE);
        b.showMap.setOnClickListener(v -> MapFragment.open(this, bh.id(), bh.lat(), bh.lng()));
        bindChat(bh);

        JSONArray rooms = d.optJSONArray("rooms");
        int roomCount = rooms == null ? 0 : rooms.length(), bedsTotal = 0;
        for (int i = 0; i < roomCount; i++) bedsTotal += rooms.getJSONObject(i).optInt("beds", 0);
        b.tileBedsValue.setText(String.valueOf(bh.availableBeds()));
        b.tileRoomsValue.setText(String.valueOf(roomCount));
        b.tileRatingValue.setText(ListRules.ratingText(bh.avgRating(), bh.reviewCount()));
        String freeText = ListRules.freeText(bh.water(), bh.electricity(), bh.wifi());
        b.free.setText(freeText);
        b.free.setVisibility(freeText.isEmpty() ? View.GONE : View.VISIBLE);

        showPhotos(d, bh);
        showAmenities(d.optJSONArray("amenity_list"));
        showRules(d.optJSONArray("rules"));
        showContact(d.optJSONArray("contact"));
        showReviews(reviews);
        b.directions.bind(this, location, bh);

        b.loading.setVisibility(View.GONE);
        b.content.setVisibility(View.VISIBLE);
        b.chatBar.setVisibility(View.VISIBLE);
    }

    /** Stage 7: get_or_create_conversation (idempotent), then open the chat. An owner cannot chat with themselves. */
    private void bindChat(BoardingHouse bh) {
        String me = AuthService.get().userId();
        if (me != null && me.equals(bh.ownerId())) {
            b.chat.setEnabled(false);
            b.chat.setText(R.string.detail_chat_own);
            return;
        }
        b.chat.setEnabled(true);
        b.chat.setText(R.string.detail_chat);
        b.chat.setOnClickListener(v -> {
            b.chat.setEnabled(false);
            b.chat.setText(R.string.detail_chat_opening);
            Async.run(() -> new ChatService().openConversation(bh.id()), (String convId) -> {
                if (b == null) return;
                b.chat.setEnabled(true);
                b.chat.setText(R.string.detail_chat);
                ChatFragment.open(this, convId, bh.name(), bh.name());
            }, e -> {
                if (b == null) return;
                b.chat.setEnabled(true);
                b.chat.setText(R.string.detail_chat);
                ListArea.toast(this, Connectivity.isNetworkError(e) ? getString(R.string.list_offline) : ErrorText.full(getString(R.string.detail_chat_error), e));
            });
        });
    }

    private void showPhotos(JSONObject d, BoardingHouse bh) {
        List<String> urls = new ArrayList<>();
        JSONArray ph = d.optJSONArray("photos");
        if (ph != null) for (int i = 0; i < ph.length(); i++) {
            JSONObject o = ph.optJSONObject(i);
            String u = o == null ? "" : ListRules.firstText(o.optString("url", ""));
            if (!u.isEmpty()) urls.add(u);
        }
        if (urls.isEmpty() && bh.photoUrl() != null && !bh.photoUrl().isEmpty()) urls.add(bh.photoUrl());
        final int n = urls.size();
        b.photos.setAdapter(new PhotoAdapter(urls));
        b.noPhotos.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        b.photoCount.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
        b.photoCount.setText(n > 0 ? "1 / " + n : "");
        b.photos.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { if (b != null) b.photoCount.setText((position + 1) + " / " + n); }
        });
    }

    /** Amenities as two-column tiles: the app's own line icon in a tinted circle + the name. */
    private void showAmenities(JSONArray list) {
        b.amenities.removeAllViews();
        List<Amenity> items = new ArrayList<>();
        if (list != null) for (int i = 0; i < list.length(); i++) {
            JSONObject row = list.optJSONObject(i);
            JSONObject am = row == null ? null : row.optJSONObject("amenities");
            if (am == null) continue;
            try { items.add(Amenity.from(am)); } catch (Exception ignored) { /* a malformed amenity row is skipped */ }
        }
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < items.size(); i += 2) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            android.widget.LinearLayout.LayoutParams rp = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rp.topMargin = (int) (8 * dp);
            for (int k = 0; k < 2; k++) {
                android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (k == 1) cp.setMarginStart((int) (8 * dp));
                if (i + k < items.size()) row.addView(amenityTile(items.get(i + k), dp), cp);
                else row.addView(new View(requireContext()), cp);
            }
            b.amenities.addView(row, rp);
        }
        int vis = items.isEmpty() ? View.GONE : View.VISIBLE;
        b.amenities.setVisibility(vis);
        b.amenitiesTitle.setVisibility(vis);
        if (!items.isEmpty()) b.free.setVisibility(View.GONE);          // the "Free Wi-Fi" line under the tiles would only repeat them
    }

    private View amenityTile(Amenity a, float dp) {
        android.widget.LinearLayout tile = new android.widget.LinearLayout(requireContext());
        tile.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        tile.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tile.setBackgroundResource(R.drawable.bg_amenity_tile);
        int pad = (int) (10 * dp);
        tile.setPadding(pad, pad, pad, pad);
        android.widget.FrameLayout bubble = new android.widget.FrameLayout(requireContext());
        bubble.setBackgroundResource(R.drawable.bg_icon_circle_tint);
        android.widget.ImageView icon = new android.widget.ImageView(requireContext());
        icon.setImageResource(AmenityIcons.res(a.name()));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bofo_primary)));
        int s = (int) (18 * dp);
        bubble.addView(icon, new android.widget.FrameLayout.LayoutParams(s, s, android.view.Gravity.CENTER));
        int bs = (int) (34 * dp);
        tile.addView(bubble, new android.widget.LinearLayout.LayoutParams(bs, bs));
        android.widget.TextView t = new android.widget.TextView(requireContext());
        t.setText(a.name());
        t.setTextSize(14f);
        t.setMaxLines(2);
        t.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bofo_text));
        android.widget.LinearLayout.LayoutParams tp = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart((int) (10 * dp));
        tile.addView(t, tp);
        return tile;
    }

    /** House rules written by the owner: one bullet per line; the whole block is hidden when there are none. */
    private void showRules(JSONArray rules) {
        b.rulesList.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        if (rules != null) for (int i = 0; i < rules.length(); i++) {
            String r = rules.optString(i, "").trim();
            if (r.isEmpty()) continue;
            android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.TOP);
            android.widget.ImageView ic = new android.widget.ImageView(requireContext());
            ic.setImageResource(R.drawable.ic_am_rule);
            ic.setImageTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bofo_primary)));
            int s = (int) (20 * dp);
            android.widget.LinearLayout.LayoutParams ip = new android.widget.LinearLayout.LayoutParams(s, s);
            ip.topMargin = (int) (1 * dp);
            row.addView(ic, ip);
            android.widget.TextView t = new android.widget.TextView(requireContext());
            t.setText(r);
            t.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bofo_text));
            t.setTextSize(14f);
            android.widget.LinearLayout.LayoutParams tp = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tp.setMarginStart((int) (10 * dp));
            row.addView(t, tp);
            android.widget.LinearLayout.LayoutParams rp = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (b.rulesList.getChildCount() > 0) rp.topMargin = (int) (10 * dp);
            b.rulesList.addView(row, rp);
        }
        int vis = b.rulesList.getChildCount() == 0 ? View.GONE : View.VISIBLE;
        b.rulesList.setVisibility(vis);
        b.rulesTitle.setVisibility(vis);
    }

    private void showContact(JSONArray contact) {
        JSONObject c = contact != null && contact.length() > 0 ? contact.optJSONObject(0) : null;
        String phone = c == null ? "" : ListRules.firstText(c.optString("phone", ""));
        String fb = c == null ? "" : ListRules.firstText(c.optString("facebook", ""));
        String mail = c == null ? "" : ListRules.firstText(c.optString("email", ""));
        b.contactCall.setVisibility(phone.isEmpty() ? View.GONE : View.VISIBLE);
        b.contactFacebook.setVisibility(fb.isEmpty() ? View.GONE : View.VISIBLE);
        b.contactEmail.setVisibility(mail.isEmpty() ? View.GONE : View.VISIBLE);
        b.contactNone.setVisibility(phone.isEmpty() && fb.isEmpty() && mail.isEmpty() ? View.VISIBLE : View.GONE);
        b.contactCall.setText(getString(R.string.detail_call, phone));
        b.contactFacebook.setText(R.string.detail_facebook);
        b.contactEmail.setText(getString(R.string.detail_email, mail));
        b.contactCall.setOnClickListener(v -> launch(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))));
        b.contactEmail.setOnClickListener(v -> launch(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(mail)))));
        b.contactFacebook.setOnClickListener(v -> launch(new Intent(Intent.ACTION_VIEW, Uri.parse(facebookUrl(fb)))));
    }

    /** A full link is used as is; "something.com/page" gets https://; a bare name becomes an m.me chat link. */
    static String facebookUrl(String v) {
        String t = v.trim();
        if (t.startsWith("http://") || t.startsWith("https://")) return t;
        if (t.contains("/") || t.contains(".")) return "https://" + t;
        return "https://m.me/" + Uri.encode(t);
    }

    private void launch(Intent i) {
        try { startActivity(i); }
        catch (ActivityNotFoundException e) { ListArea.toast(this, getString(R.string.detail_open_failed)); }
    }

    private void showReviews(JSONArray rv) {
        int n = rv == null ? 0 : rv.length();
        b.reviewsTitle.setText(getString(R.string.detail_reviews, n));
        b.reviews.removeAllViews();
        b.reviewsEmpty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (int i = 0; i < Math.min(n, MAX_REVIEWS); i++) {
            JSONObject o = rv.optJSONObject(i);
            if (o == null) continue;
            ItemReviewBinding r = ItemReviewBinding.inflate(inf, b.reviews, false);
            String name = ListRules.firstText(o.optString("reviewer_name", ""), o.optString("full_name", ""), o.optString("student_name", ""), o.optString("name", ""));
            r.reviewerName.setText(name.isEmpty() ? getString(R.string.detail_reviewer_anon) : name);
            r.reviewStars.setText(ListRules.stars(o.optInt("rating", 0)));
            String comment = ListRules.firstText(o.optString("comment", ""));
            r.reviewComment.setText(comment);
            r.reviewComment.setVisibility(comment.isEmpty() ? View.GONE : View.VISIBLE);
            r.reviewDate.setText(ListRules.shortDate(ListRules.firstText(o.optString("created_at", ""))));
            String photo = ListRules.firstText(o.optString("reviewer_photo", ""), o.optString("photo_url", ""), o.optString("avatar_url", ""), o.optString("student_photo", ""));
            if (!photo.isEmpty()) Glide.with(r.reviewerPhoto).load(photo).circleCrop().placeholder(R.drawable.mascot_happy).into(r.reviewerPhoto);
            b.reviews.addView(r.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        token++;
        if (location != null) location.stop();
        b = null;
        super.onDestroyView();
    }

    /** Full-width photos for the ViewPager2. */
    private static final class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.VH> {
        private final List<String> urls;
        PhotoAdapter(List<String> urls) { this.urls = urls; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setContentDescription(parent.getContext().getString(R.string.cd_bh_photo));
            return new VH(iv);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            Glide.with(h.iv).load(urls.get(position)).centerCrop().placeholder(R.color.bofo_skeleton).into(h.iv);
        }
        @Override public int getItemCount() { return urls.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final ImageView iv;
            VH(ImageView iv) { super(iv); this.iv = iv; }
        }
    }
}
