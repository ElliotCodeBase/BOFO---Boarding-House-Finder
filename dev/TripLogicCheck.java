// Plain-Java check of the trip logic (no JavaFX, no network). Run from the project folder (PowerShell or any shell):
//   javac -d out-check src/main/java/com/bofo/finder/util/Geo.java src/main/java/com/bofo/finder/util/Trip.java src/main/java/com/bofo/finder/util/TransitMatch.java dev/TripLogicCheck.java
//   java -cp out-check TripLogicCheck          (last line says ALL PASSED)
import com.bofo.finder.util.*;
import java.util.*;

public class TripLogicCheck {
    static int fails = 0;
    static void eq(String n, Object a, Object b) { if (!Objects.equals(a, b)) { fails++; System.out.println("FAIL " + n + ": got " + a + " expected " + b); } else System.out.println("ok   " + n); }
    static void ok(String n, boolean c) { if (!c) { fails++; System.out.println("FAIL " + n); } else System.out.println("ok   " + n); }

    // a straight road going east from (9.30,123.30), one point per ~111 m
    static double[][] road(double lat, double lng0, int n) { double[][] p = new double[n][]; for (int i = 0; i < n; i++) p[i] = new double[]{lat, lng0 + i * 0.001}; return p; }
    static double[][] rev(double[][] p) { double[][] r = new double[p.length][]; for (int i = 0; i < p.length; i++) r[i] = p[p.length - 1 - i]; return r; }

    public static void main(String[] a) {
        // time + fare formatting
        eq("fmtTime 17", Geo.fmtTime(17), "17 min");
        eq("fmtTime 65", Geo.fmtTime(65), "1 h 5 min");
        eq("fmtTime 1266 (the screenshot)", Geo.fmtTime(1266), "21 h 6 min");
        eq("fmtTime 120", Geo.fmtTime(120), "2 h");
        eq("jeep trad 3 km", Trip.jeepTraditional(3), 14);
        eq("jeep trad 4 km", Trip.jeepTraditional(4), 14);
        eq("jeep trad 6 km", Trip.jeepTraditional(6), 18);
        eq("jeep modern 4 km", Trip.jeepModern(4), 17);
        eq("jeep modern 9 km", Trip.jeepModern(9), 29);
        eq("peso range", Trip.peso(14, 17), "\u20B114\u201317");
        eq("peso same", Trip.peso(0, 0), "\u20B10");
        eq("peso unknown", Trip.peso(-1, -1), "");

        // chain: pieces in wrong order/orientation still make one east-going path
        double[][] w1 = road(9.30, 123.300, 5), w2 = rev(road(9.30, 123.304, 5)), w3 = road(9.30, 123.308, 5);
        double[][] ch = TransitMatch.chain(List.of(rev(w1), w2, w3));
        ok("chain joins 3 pieces without duplicate joints", ch.length == 13);
        double lo = Math.min(ch[0][1], ch[ch.length - 1][1]), hi = Math.max(ch[0][1], ch[ch.length - 1][1]);
        ok("chain spans the whole road", Math.abs(lo - 123.300) < 1e-6 && Math.abs(hi - 123.312) < 1e-6);
        boolean mono = true; for (int i = 2; i < ch.length; i++) if ((ch[i][1] - ch[i - 1][1]) * (ch[1][1] - ch[0][1]) <= 0) mono = false;
        ok("chain is one consistent direction", mono);

        // matching: a line east along lat 9.30 from 123.300 to 123.330 (3.3 km)
        double[][] east = road(9.30, 123.300, 31);
        TransitMatch.Line eastLine = new TransitMatch.Line("12A", "Poblacion - Bajumpandan", "", "bus", east);
        TransitMatch.Line westLine = new TransitMatch.Line("12B", "Bajumpandan - Poblacion", "", "bus", rev(east));
        double[] from = {9.3003, 123.3005}, to = {9.3004, 123.3252};          // about 30 m / 40 m from the road
        List<TransitMatch.Match> ms = TransitMatch.match(List.of(eastLine, westLine), from, to, 450, 3);
        eq("only the line going the right way matches", ms.size(), 1);
        eq("matched line", ms.get(0).line().ref(), "12A");
        ok("board near start", ms.get(0).walkToM() > 50 && ms.get(0).walkToM() < 80);
        ok("ride about 2.75 km", Math.abs(ms.get(0).rideM() - 2747) < 60);
        eq("opposite direction trip", TransitMatch.match(List.of(eastLine, westLine), to, from, 450, 3).get(0).line().ref(), "12B");
        // too far from the road
        ok("no match when the road is 1 km away", TransitMatch.match(List.of(eastLine), new double[]{9.31, 123.3005}, new double[]{9.31, 123.3252}, 450, 3).isEmpty());
        // gap in the data (jump of 2.2 km) is rejected
        double[][] gap = new double[][]{{9.30, 123.300}, {9.30, 123.301}, {9.30, 123.321}, {9.30, 123.322}};
        ok("gapped line is rejected", TransitMatch.match(List.of(new TransitMatch.Line("G", "gap", "", "bus", gap)), new double[]{9.30, 123.300}, new double[]{9.30, 123.322}, 450, 3).isEmpty());
        // wild detour is rejected: out and back on a loop so ride >> straight distance
        List<double[]> loop = new ArrayList<>(); for (int i = 0; i <= 20; i++) loop.add(new double[]{9.30 + i * 0.001, 123.300}); for (int i = 20; i >= 0; i--) loop.add(new double[]{9.30 + i * 0.001, 123.3005});
        ok("detour (ride >> straight) is rejected", TransitMatch.match(List.of(new TransitMatch.Line("L", "loop", "", "bus", loop.toArray(new double[0][]))), new double[]{9.3005, 123.300}, new double[]{9.3005, 123.3005}, 450, 3).isEmpty());
        // two relations with the same name keep only the best, results sorted by cost
        TransitMatch.Line dup = new TransitMatch.Line("12A", "Poblacion - Bajumpandan", "", "bus", road(9.3008, 123.300, 31));
        eq("same-name lines collapse to one", TransitMatch.match(List.of(eastLine, dup), from, to, 450, 3).size(), 1);
        TransitMatch.Line other = new TransitMatch.Line("7", "Other", "", "bus", road(9.3025, 123.300, 31));
        eq("closer line ranks first", TransitMatch.match(List.of(other, eastLine), from, to, 450, 3).get(0).line().ref(), "12A");

        // options and the recommendation
        double[][] p = {{0, 0}, {0, 1}};
        Trip.Option walk800 = Trip.walk(p, 800, false), walk3000 = Trip.walk(p, 3000, false);
        eq("walk 800 m = 10 min", walk800.minutes(), 10);
        eq("walk 3 km = 36 min", walk3000.minutes(), 36);
        eq("walk is free", walk800.fareText(), "\u20B10");
        eq("a walk of 1 km or less always wins", Trip.best(List.of(Trip.tricycle(p, 800, false), walk800)), 1);
        Trip.Option jeep = Trip.jeepney("12A", p, 5200, 300, false);
        eq("long walk loses to a cheap jeepney", Trip.best(List.of(Trip.walk(p, 4500, false), jeep)), 1);
        ok("jeepney fare range ok", jeep.fareLow() == 16 && jeep.fareHigh() == 20);
        eq("far option has no fare", Trip.far(p, 105500, false).fareText(), "");
        eq("far 105 km is hours, not 1266 min walking", Geo.fmtTime(Trip.far(p, 105500, false).minutes()), "3 h 1 min");
        eq("concat drops duplicated joint", Trip.concat(new double[][]{{1, 1}, {2, 2}}, new double[][]{{2, 2}, {3, 3}}).length, 3);
        // checked fares, local lines, one-line summary (v1.9.0)
        Trip.Option checked = Trip.jeepney("L1", p, 2800, 160, false, 12, 12, "Checked Oct 2026.");
        eq("checked fare replaces the formula", checked.fareText(), "\u20B112");
        ok("checked option says so and shows the note", checked.detail().contains("checked by the BOFO team") && checked.detail().contains("Checked Oct 2026."));
        ok("formula option does not claim a check", !Trip.jeepney("12A", p, 2800, 160, false).detail().contains("checked by the BOFO team"));
        ok("summary: icon, word, minutes, fare, km", Trip.tricycle(p, 3400, false).summary().contains("Tricycle") && Trip.tricycle(p, 3400, false).summary().contains("\u20B110\u201320") && Trip.tricycle(p, 3400, false).summary().contains("3.4 km"));
        ok("summary of a walk has no fare", !Trip.walk(p, 600, false).summary().contains("\u20B1"));
        double[][] lp = road(9.30, 123.300, 31);
        TransitMatch.Line osmLine = new TransitMatch.Line("12A", "Poblacion", "", "bus", lp);
        TransitMatch.Line localLine = new TransitMatch.Line("L1", "Local", "", "local", road(9.3006, 123.300, 31), "n", 12, 12);
        eq("a hand-made line outranks OpenStreetMap at similar distance", TransitMatch.match(List.of(osmLine, localLine), from, to, 450, 3).get(0).line().ref(), "L1");
        System.out.println(fails == 0 ? "\nALL PASSED" : "\n" + fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
