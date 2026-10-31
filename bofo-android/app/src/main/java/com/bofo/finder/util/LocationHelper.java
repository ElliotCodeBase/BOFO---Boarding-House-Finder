package com.bofo.finder.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * Location for one Fragment. Create it in the Fragment's constructor or onCreate (it registers the permission launcher).
 * Honours Settings > Use my location: when that switch is off nothing is requested and {@link Callback#off()} is called.
 * All callbacks run on the main thread.
 */
public final class LocationHelper {

    public interface Callback {
        void got(double lat, double lng);
        /** Settings > Use my location is off. */
        void off();
        /** The user said no to the permission. */
        void denied();
        /** No position could be read (location services off, no signal). */
        void failed();
        /** The phone's Location switch (quick settings) is off, so no app can read a position. Default: same as {@link #failed()}. */
        default void servicesOff() { failed(); }
    }

    private final Fragment fragment;
    private final ActivityResultLauncher<String[]> permission;
    private Callback pending;
    private boolean pendingWatch;
    private FusedLocationProviderClient client;
    private LocationCallback watcher;
    private CancellationTokenSource once;

    public LocationHelper(Fragment fragment) {
        this.fragment = fragment;
        this.permission = fragment.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Callback cb = pending; boolean watch = pendingWatch;
            pending = null;
            if (cb == null) return;
            if (granted(fragment.requireContext())) { if (watch) startWatch(cb); else readOnce(cb); }
            else cb.denied();
        });
    }

    public static boolean granted(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** One position (used by "Near me"). */
    public void requestOnce(Callback cb) { begin(cb, false); }

    /** Live position updates until {@link #stop()} (used by the live dot). The first fix is also delivered quickly from the last known position. */
    public void startWatching(Callback cb) { begin(cb, true); }

    private void begin(Callback cb, boolean watch) {
        Context c = fragment.getContext();
        if (c == null) return;
        if (!AppPrefs.useLocation(c)) { cb.off(); return; }
        if (!granted(c)) {
            pending = cb; pendingWatch = watch;
            permission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        if (watch) startWatch(cb); else readOnce(cb);
    }

    @SuppressLint("MissingPermission")   // checked in begin() / the permission result
    private void readOnce(Callback cb) {
        Context c = fragment.getContext();
        if (c == null) return;
        client = client != null ? client : LocationServices.getFusedLocationProviderClient(c);
        if (once != null) once.cancel();
        once = new CancellationTokenSource();
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, once.getToken())
                .addOnSuccessListener(loc -> {
                    if (loc != null) { if (fragment.isAdded()) cb.got(loc.getLatitude(), loc.getLongitude()); }
                    else lastKnown(cb);
                })
                .addOnFailureListener(e -> { Log.w("BOFO", "Fused location failed", e); lastKnown(cb); });
    }

    @SuppressLint("MissingPermission")
    private void lastKnown(Callback cb) {
        if (client == null || !fragment.isAdded()) return;
        client.getLastLocation()
                .addOnSuccessListener(loc -> { if (fragment.isAdded()) { if (loc != null) cb.got(loc.getLatitude(), loc.getLongitude()); else platform(cb); } })
                .addOnFailureListener(e -> { if (fragment.isAdded()) platform(cb); });
    }

    /** Last resort: Android's own LocationManager (no Google Play services needed). Last known fix from any provider, else one fresh fix (12 s). */
    @SuppressLint("MissingPermission")
    private void platform(Callback cb) {
        Context c = fragment.getContext();
        if (c == null || !fragment.isAdded()) return;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !LocationManagerCompat.isLocationEnabled(lm)) { Log.w("BOFO", "Location switch is off"); cb.servicesOff(); return; }
        try {
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) { cb.got(best.getLatitude(), best.getLongitude()); return; }
            String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                    : lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
            if (provider == null) { cb.failed(); return; }
            final boolean[] done = {false};
            final CancellationSignal cancel = new CancellationSignal();
            final Handler h = new Handler(Looper.getMainLooper());
            final Runnable timeout = () -> { if (!done[0]) { done[0] = true; cancel.cancel(); Log.w("BOFO", "Location: no fix in 12 s"); if (fragment.isAdded()) cb.failed(); } };
            h.postDelayed(timeout, 12_000);
            LocationManagerCompat.getCurrentLocation(lm, provider, cancel, ContextCompat.getMainExecutor(c), l -> {
                if (done[0]) return;
                done[0] = true; h.removeCallbacks(timeout);
                if (!fragment.isAdded()) return;
                if (l != null) cb.got(l.getLatitude(), l.getLongitude()); else cb.failed();
            });
        } catch (SecurityException e) {
            cb.denied();
        }
    }

    @SuppressLint("MissingPermission")
    private void startWatch(Callback cb) {
        Context c = fragment.getContext();
        if (c == null) return;
        stop();
        client = client != null ? client : LocationServices.getFusedLocationProviderClient(c);
        watcher = new LocationCallback() {
            @Override public void onLocationResult(LocationResult r) {
                Location l = r.getLastLocation();
                if (l != null && fragment.isAdded()) cb.got(l.getLatitude(), l.getLongitude());
            }
        };
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000)
                .setMinUpdateDistanceMeters(10f).build();
        try { client.requestLocationUpdates(req, watcher, Looper.getMainLooper()); }
        catch (SecurityException e) { cb.denied(); }
    }

    /** Stops live updates and any pending one-time read. Call from onStop / onDestroyView. */
    public void stop() {
        if (once != null) { once.cancel(); once = null; }
        if (client != null && watcher != null) client.removeLocationUpdates(watcher);
        watcher = null;
    }
}
