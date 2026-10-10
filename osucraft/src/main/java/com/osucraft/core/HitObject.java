package com.osucraft.core;

import java.util.ArrayList;
import java.util.List;

public final class HitObject {
    public enum Kind { CIRCLE, SLIDER, SPINNER, HOLD }

    public Kind kind = Kind.CIRCLE;
    public double x;
    public double y;
    public int time;
    public int endTime;
    public boolean newCombo;
    public int comboNumber = 1;

    // sliders
    public char curveType = 'L';
    public final List<double[]> points = new ArrayList<>();
    public int slides = 1;
    public double pixelLength;
    public double slideDuration;
    public double tickDistance;
    public SliderPath path;

    // mania
    public int column;
}
