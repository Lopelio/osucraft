package com.osucraft.core;

import java.util.ArrayList;
import java.util.List;

/** Camino de un slider convertido en una polilínea con longitud exacta (en píxeles osu). */
public final class SliderPath {
    private final double[] px;
    private final double[] py;
    private final double[] cum;
    public final double length;

    private SliderPath(List<double[]> poly, double wanted) {
        List<double[]> pts = new ArrayList<>();
        for (double[] p : poly) {
            if (pts.isEmpty() || dist(pts.get(pts.size() - 1), p) > 1e-6) pts.add(p);
        }
        if (pts.size() == 1) pts.add(new double[]{pts.get(0)[0] + 0.01, pts.get(0)[1]});

        double total = 0;
        for (int i = 1; i < pts.size(); i++) total += dist(pts.get(i - 1), pts.get(i));

        if (wanted > 0 && total > wanted) {
            // recortar
            List<double[]> cut = new ArrayList<>();
            cut.add(pts.get(0));
            double acc = 0;
            for (int i = 1; i < pts.size(); i++) {
                double seg = dist(pts.get(i - 1), pts.get(i));
                if (acc + seg >= wanted) {
                    double f = seg <= 0 ? 0 : (wanted - acc) / seg;
                    double[] a = pts.get(i - 1);
                    double[] b = pts.get(i);
                    cut.add(new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f});
                    break;
                }
                acc += seg;
                cut.add(pts.get(i));
            }
            pts = cut;
            total = wanted;
        } else if (wanted > 0 && total < wanted) {
            // extender el último tramo
            double[] a = pts.get(pts.size() - 2);
            double[] b = pts.get(pts.size() - 1);
            double seg = dist(a, b);
            if (seg > 0) {
                double f = (wanted - total) / seg;
                pts.add(new double[]{b[0] + (b[0] - a[0]) * f, b[1] + (b[1] - a[1]) * f});
                total = wanted;
            }
        }

        int n = pts.size();
        px = new double[n];
        py = new double[n];
        cum = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = pts.get(i)[0];
            py[i] = pts.get(i)[1];
            cum[i] = i == 0 ? 0 : cum[i - 1] + Math.hypot(px[i] - px[i - 1], py[i] - py[i - 1]);
        }
        this.length = cum[n - 1];
    }

    public static SliderPath build(char type, double sx, double sy, List<double[]> ctrl, double pixelLength) {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{sx, sy});
        pts.addAll(ctrl);
        List<double[]> poly = null;
        switch (type) {
            case 'P' -> {
                if (pts.size() == 3) poly = perfect(pts, pixelLength);
                if (poly == null) poly = pts.size() == 3 ? pts : bezier(pts);
            }
            case 'C' -> poly = catmull(pts);
            case 'B' -> poly = bezier(pts);
            default -> poly = pts;
        }
        return new SliderPath(poly, pixelLength);
    }

    /** Escribe en out[0], out[1] la posición a progress (0..1) del recorrido. */
    public void at(double progress, double[] out) {
        double d = Math.max(0, Math.min(1, progress)) * length;
        int n = px.length;
        int i = 1;
        while (i < n - 1 && cum[i] < d) i++;
        double seg = cum[i] - cum[i - 1];
        double f = seg <= 0 ? 0 : (d - cum[i - 1]) / seg;
        out[0] = px[i - 1] + (px[i] - px[i - 1]) * f;
        out[1] = py[i - 1] + (py[i] - py[i - 1]) * f;
    }

    /** Puntos de la polilínea para dibujar el cuerpo. */
    public double[][] sample(double spacing) {
        int count = Math.max(2, (int) Math.ceil(length / Math.max(1.0, spacing)) + 1);
        double[][] r = new double[count][2];
        double[] tmp = new double[2];
        for (int i = 0; i < count; i++) {
            at(i / (double) (count - 1), tmp);
            r[i][0] = tmp[0];
            r[i][1] = tmp[1];
        }
        return r;
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static List<double[]> perfect(List<double[]> p, double wanted) {
        double ax = p.get(0)[0], ay = p.get(0)[1];
        double bx = p.get(1)[0], by = p.get(1)[1];
        double cx = p.get(2)[0], cy = p.get(2)[1];
        double d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
        if (Math.abs(d) < 1e-9) return null;
        double a2 = ax * ax + ay * ay, b2 = bx * bx + by * by, c2 = cx * cx + cy * cy;
        double ux = (a2 * (by - cy) + b2 * (cy - ay) + c2 * (ay - by)) / d;
        double uy = (a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax)) / d;
        double r = Math.hypot(ax - ux, ay - uy);
        if (r > 5000 || r < 1e-6) return null;
        double t0 = Math.atan2(ay - uy, ax - ux);
        double t2 = Math.atan2(cy - uy, cx - ux);
        double cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        double dir = cross > 0 ? 1 : -1;
        double twoPi = Math.PI * 2;
        double span = dir > 0 ? norm(t2 - t0) : norm(t0 - t2);
        if (wanted > 0) span = Math.min(wanted / r, twoPi);
        int steps = Math.max(4, (int) Math.ceil(span * r / 4.0));
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double t = t0 + dir * span * i / steps;
            out.add(new double[]{ux + r * Math.cos(t), uy + r * Math.sin(t)});
        }
        return out;
    }

    private static double norm(double a) {
        double twoPi = Math.PI * 2;
        return ((a % twoPi) + twoPi) % twoPi;
    }

    private static List<double[]> bezier(List<double[]> pts) {
        List<double[]> out = new ArrayList<>();
        List<double[]> seg = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            if (!seg.isEmpty() && dist(seg.get(seg.size() - 1), p) < 1e-9 && seg.size() > 1) {
                // ancla roja: cierra el segmento
                seg.add(p);
                addBezier(seg, out);
                seg = new ArrayList<>();
                seg.add(p);
            } else {
                seg.add(p);
            }
        }
        if (seg.size() > 1) addBezier(seg, out);
        if (out.isEmpty()) out.addAll(pts);
        return out;
    }

    private static void addBezier(List<double[]> ctrl, List<double[]> out) {
        int n = ctrl.size();
        double len = 0;
        for (int i = 1; i < n; i++) len += dist(ctrl.get(i - 1), ctrl.get(i));
        int steps = (int) Math.max(8, Math.min(400, Math.ceil(len / 3.0)));
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;
            for (int i = 0; i < n; i++) {
                xs[i] = ctrl.get(i)[0];
                ys[i] = ctrl.get(i)[1];
            }
            for (int k = n - 1; k > 0; k--) {
                for (int i = 0; i < k; i++) {
                    xs[i] = xs[i] + (xs[i + 1] - xs[i]) * t;
                    ys[i] = ys[i] + (ys[i + 1] - ys[i]) * t;
                }
            }
            out.add(new double[]{xs[0], ys[0]});
        }
    }

    private static List<double[]> catmull(List<double[]> p) {
        List<double[]> out = new ArrayList<>();
        int n = p.size();
        if (n < 2) return new ArrayList<>(p);
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = p.get(Math.max(i - 1, 0));
            double[] p1 = p.get(i);
            double[] p2 = p.get(i + 1);
            double[] p3 = p.get(Math.min(i + 2, n - 1));
            int steps = 30;
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double t2 = t * t, t3 = t2 * t;
                double x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                        + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                double y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                        + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
                out.add(new double[]{x, y});
            }
        }
        return out;
    }
}
