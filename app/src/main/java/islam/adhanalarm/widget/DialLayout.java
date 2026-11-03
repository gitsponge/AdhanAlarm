package islam.adhanalarm.widget;

/**
 * Where the prayers' names go round a big dial. Each sits at the end of a leader line from the middle of
 * its window, in a row above or below the dial or a column beside it. Every way of sharing the names out
 * is tried, and the one that leaves room for the biggest dial wins. Pure geometry in pixels, so it can be
 * tested without a phone.
 */
final class DialLayout {

    /** Where a label sits: in a row or column on a pointer, or AROUND, just outside its window with no pointer */
    static final int TOP = 0, BOTTOM = 1, LEFT = 2, RIGHT = 3, AROUND = 4;

    // In dp. A leader starts a little off the rim and runs straight out from it before turning for its
    // label, and stops a little short of the label. Rows keep a gap from the rim and space between their
    // labels; columns a gap from the rim and space between theirs.
    private static final float RIM_GAP = 1.5f, STUB = 3, END_GAP = 1.5f;
    /** How far out from the rim a leader turns for its label, in dp: beside a dial as wide as the box, that's past the box's edge */
    static final float ELBOW = RIM_GAP + STUB;
    private static final float ROW_GAP = 8, ROW_SPACE = 11, COLUMN_GAP = 12, COLUMN_SPACE = 6;
    /** The highlight round the next prayer's name. Room for it is kept round every label, so the layout stays put as the day goes on. */
    static final float PILL_X = 5, PILL_Y = 2.5f;
    /** A leader meets its label at least this far in from the label's ends, so it plainly belongs to it */
    private static final float LEADER_INSET = 5;
    /** The smallest dial's radius worth looking for, in dp */
    private static final float MIN_RADIUS = 20;
    /**
     * How much bigger the dial has to get for each name moved into a side column, in a squarish space, where
     * rows keep the dial in the middle as at the other sizes. The cost fades away as the space widens to 1.3
     * times its height, where columns beside the dial are the natural place for the names.
     */
    private static final float COLUMN_COST = 0.04f, WIDE_FROM = 1.0f, WIDE = 1.3f;
    /** The search steps down this far at a time, then back up in quarters of it */
    private static final float STEP = 2;
    /** Around the ring: the gap from the rim to a label, in dp, and how far round, in degrees, a label may edge from its window's middle to clear its neighbours */
    private static final float AROUND_GAP = 10, AROUND_SLIDE = 22;

    static final class Label {
        /** Degrees clockwise from the top, at the middle of its window */
        final float theta;
        final float width, height;
        /** Whether it's the next prayer's, which wears the highlight and so keeps its leader clear of it */
        final boolean next;

        int slot;
        float left, top, right, bottom;
        /** The leader: from the rim, out to the elbow, then to the label */
        float rimX, rimY, elbowX, elbowY, endX, endY;
        private float mid; // Its centre along its row or column while it's being placed

        Label(float theta, float width, float height, boolean next) {
            this.theta = theta;
            this.width = width;
            this.height = height;
            this.next = next;
        }
    }

    private final float dp;
    float centreX, centreY, radius;

    DialLayout(float pixelsPerDp) {
        dp = pixelsPerDp;
    }

    /**
     * Places the dial and its labels in the box, as big as they'll go. False if the labels don't fit round
     * a dial of any useful size.
     */
    boolean fit(float left, float top, float right, float bottom, Label[] labels) {
        int count = labels.length;
        int[][] choices = new int[count][];
        for (int i = 0; i < count; i++) {
            choices[i] = slotsFor(labels[i].theta);
        }
        int[] pick = new int[count], slots = new int[count], best = null;
        int bestScore = 0, bestColumns = 0;
        float bestLength = 0, bestRadius = 0;
        while (true) {
            for (int i = 0; i < count; i++) {
                slots[i] = choices[i][pick[i]];
            }
            int columns = 0;
            for (int slot : slots) {
                if (slot == LEFT || slot == RIGHT) columns++;
            }
            float share = 1 - columnCost(right - left, bottom - top) * columns;
            // Only a dial big enough to beat or match the best so far is worth looking for
            float floor = Math.max(MIN_RADIUS * dp, best == null ? 0 : (bestScore - 0.5f) * dp / share - dp);
            if (rowsFit(labels, slots, right - left)) {
                float found = largest(labels, slots, left, top, right, bottom, floor);
                if (found > 0) {
                    int score = Math.round(found * share / dp);
                    float length = leaderLength(labels);
                    if (best == null || score > bestScore || score == bestScore && (columns < bestColumns
                            || columns == bestColumns && length < bestLength)) {
                        best = slots.clone();
                        bestScore = score;
                        bestColumns = columns;
                        bestLength = length;
                        bestRadius = found;
                    }
                }
            }
            int k = 0;
            while (k < count && ++pick[k] == choices[k].length) {
                pick[k++] = 0;
            }
            if (k == count) break;
        }
        if (best == null) return false;
        return tryRadius(labels, best, left, top, right, bottom, bestRadius); // Leaves everything placed
    }

    /**
     * Places each label just outside the middle of its window, upright and with no pointer, neighbours eased
     * apart round the circle, for the biggest dial that leaves room. On a big screen being next to its arc is
     * enough to show which window a name belongs to. False if no useful dial fits that way.
     */
    boolean fitAround(float left, float top, float right, float bottom, Label[] labels) {
        float r = Math.min(right - left, bottom - top) / 2;
        for (; r > MIN_RADIUS * dp; r -= STEP * dp) {
            if (tryAround(labels, left, top, right, bottom, r)) break;
        }
        if (r <= MIN_RADIUS * dp) return false;
        for (float up = r + STEP * dp * 0.75f; up > r; up -= STEP * dp / 4) {
            if (tryAround(labels, left, top, right, bottom, up)) return true;
        }
        return tryAround(labels, left, top, right, bottom, r);
    }

    private boolean tryAround(Label[] labels, float left, float top, float right, float bottom, float r) {
        float x = (left + right) / 2, y = (top + bottom) / 2;
        float[] angles = placeAround(labels, x, y, r);
        float minX = x - r, maxX = x + r, minY = y - r, maxY = y + r;
        for (Label label : labels) {
            minX = Math.min(minX, label.left);
            maxX = Math.max(maxX, label.right);
            minY = Math.min(minY, label.top);
            maxY = Math.max(maxY, label.bottom);
        }
        x += (left + right) / 2 - (minX + maxX) / 2;
        y += (top + bottom) / 2 - (minY + maxY) / 2;
        angles = placeAround(labels, x, y, r);
        centreX = x;
        centreY = y;
        radius = r;
        for (int i = 0; i < labels.length; i++) {
            float slide = Math.abs(angles[i] - labels[i].theta);
            if (Math.min(slide, 360 - slide) > AROUND_SLIDE) return false; // Too far from its window to tell which it names
        }
        return isClean(labels, left, top, right, bottom);
    }

    /** Sets each label beside its window, then eases neighbours that touch apart round the circle; returns where each ended up */
    private float[] placeAround(Label[] labels, float x, float y, float r) {
        int count = labels.length;
        float[] angles = new float[count];
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            angles[i] = labels[i].theta;
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(labels[a].theta, labels[b].theta));
        float px = PILL_X * dp, py = PILL_Y * dp;
        for (int round = 0; round < 160; round++) {
            for (int i = 0; i < count; i++) {
                setAround(labels[i], x, y, r, angles[i]);
            }
            boolean moved = false;
            for (int k = 0; k < count; k++) {
                Label a = labels[order[k]], b = labels[order[(k + 1) % count]];
                if (a.left - px - dp < b.right && b.left < a.right + px + dp && a.top - py - dp < b.bottom && b.top < a.bottom + py + dp) {
                    angles[order[k]] -= 0.5f;
                    angles[order[(k + 1) % count]] += 0.5f;
                    moved = true;
                }
            }
            if (!moved) break;
        }
        return angles;
    }

    /** One label just outside the rim at the given angle, its nearest corner a set gap from the rim */
    private void setAround(Label label, float x, float y, float r, float angle) {
        double radians = Math.toRadians(angle);
        float ux = (float) Math.sin(radians), uy = (float) -Math.cos(radians);
        float halfWidth = label.width / 2 + PILL_X * dp, halfHeight = label.height / 2 + PILL_Y * dp;
        float reach = Math.min(Math.abs(ux) > 1e-4f ? halfWidth / Math.abs(ux) : Float.MAX_VALUE, Math.abs(uy) > 1e-4f ? halfHeight / Math.abs(uy) : Float.MAX_VALUE);
        float distance = r + AROUND_GAP * dp + reach;
        for (int step = 0; step < 40; step++) {
            float cx = x + distance * ux, cy = y + distance * uy;
            label.left = cx - label.width / 2;
            label.right = cx + label.width / 2;
            label.top = cy - label.height / 2;
            label.bottom = cy + label.height / 2;
            if (rectToPoint(label.left - PILL_X * dp, label.top - PILL_Y * dp, label.right + PILL_X * dp, label.bottom + PILL_Y * dp, x, y) >= r + AROUND_GAP * dp) break;
            distance += dp;
        }
        label.slot = AROUND;
        // No pointer: the leader shrinks to a point on the rim
        label.rimX = label.elbowX = label.endX = x + (r + RIM_GAP * dp) * ux;
        label.rimY = label.elbowY = label.endY = y + (r + RIM_GAP * dp) * uy;
    }

    static float columnCost(float width, float height) {
        return COLUMN_COST * clamp((WIDE - width / height) / (WIDE - WIDE_FROM), 0, 1);
    }

    /**
     * The rows a label may sit in, and the columns, by where its window is. A window near the very top or
     * bottom may go in either column, which on a wide tile lets the dial take the whole height.
     */
    static int[] slotsFor(float theta) {
        double cos = Math.cos(Math.toRadians(theta)), sin = Math.sin(Math.toRadians(theta));
        int[] slots = new int[3];
        int n = 0;
        if (cos > -0.35) slots[n++] = TOP;
        if (cos < 0.35) slots[n++] = BOTTOM;
        if (sin > -0.1) slots[n++] = RIGHT;
        if (sin < 0.1) slots[n++] = LEFT;
        int[] out = new int[n];
        System.arraycopy(slots, 0, out, 0, n);
        return out;
    }

    private boolean rowsFit(Label[] labels, int[] slots, float width) {
        for (int row = TOP; row <= BOTTOM; row++) {
            float needed = -ROW_SPACE * dp;
            for (int i = 0; i < labels.length; i++) {
                if (slots[i] == row) needed += labels[i].width + ROW_SPACE * dp;
            }
            if (needed > width) return false;
        }
        return true;
    }

    /** The biggest radius above the floor at which the labels fit, or 0 */
    private float largest(Label[] labels, int[] slots, float left, float top, float right, float bottom, float floor) {
        float r = Math.min(right - left, bottom - top) / 2;
        for (; r > floor; r -= STEP * dp) {
            if (tryRadius(labels, slots, left, top, right, bottom, r)) break;
        }
        if (r <= floor) return 0;
        for (float up = r + STEP * dp * 0.75f; up > r; up -= STEP * dp / 4) {
            if (tryRadius(labels, slots, left, top, right, bottom, up)) return up;
        }
        tryRadius(labels, slots, left, top, right, bottom, r);
        return r;
    }

    /**
     * Places everything for a dial of the given radius: centred across the box with any side columns, and
     * down it with all its labels. True if it all fits.
     */
    private boolean tryRadius(Label[] labels, int[] slots, float left, float top, float right, float bottom, float r) {
        float x = (left + right) / 2, y = (top + bottom) / 2;
        place(labels, slots, x, y, r, -Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        float minX = x - r, maxX = x + r, minY = y - r, maxY = y + r;
        for (Label label : labels) {
            if (label.slot == LEFT || label.slot == RIGHT) {
                minX = Math.min(minX, label.left);
                maxX = Math.max(maxX, label.right);
            }
            minY = Math.min(minY, label.top);
            maxY = Math.max(maxY, label.bottom);
        }
        x += (left + right) / 2 - (minX + maxX) / 2;
        y += (top + bottom) / 2 - (minY + maxY) / 2;
        place(labels, slots, x, y, r, left, top, right, bottom);
        centreX = x;
        centreY = y;
        radius = r;
        return isClean(labels, left, top, right, bottom);
    }

    private void place(Label[] labels, int[] slots, float x, float y, float r, float left, float top, float right, float bottom) {
        for (int i = 0; i < labels.length; i++) {
            Label label = labels[i];
            label.slot = slots[i];
            double radians = Math.toRadians(label.theta);
            float sin = (float) Math.sin(radians), cos = (float) Math.cos(radians);
            label.rimX = x + (r + RIM_GAP * dp) * sin;
            label.rimY = y - (r + RIM_GAP * dp) * cos;
            label.elbowX = x + (r + ELBOW * dp) * sin;
            label.elbowY = y - (r + ELBOW * dp) * cos;
        }
        Label[] group = new Label[labels.length];
        for (int row = TOP; row <= BOTTOM; row++) {
            int n = gather(labels, row, group, true);
            spread(group, n, left, right, ROW_SPACE * dp, true);
            for (int k = 0; k < n; k++) {
                Label label = group[k];
                label.left = label.mid - label.width / 2;
                label.right = label.mid + label.width / 2;
                float gap = (END_GAP + (label.next ? PILL_Y : 0)) * dp;
                if (row == TOP) {
                    label.bottom = y - r - ROW_GAP * dp;
                    label.top = label.bottom - label.height;
                    label.endY = Math.min(label.elbowY, label.bottom + gap); // Never back past the elbow
                } else {
                    label.top = y + r + ROW_GAP * dp;
                    label.bottom = label.top + label.height;
                    label.endY = Math.max(label.elbowY, label.top - gap);
                }
                // Straight up or down when it can, or out to the label's far end where straight would graze the dial
                float inset = Math.min(LEADER_INSET * dp, label.width / 2);
                label.endX = clamp(label.elbowX, label.left + inset, label.right - inset);
                if (segmentToPoint(label.elbowX, label.elbowY, label.endX, label.endY, x, y) < r + RIM_GAP * dp) {
                    label.endX = label.elbowX < x ? label.left + inset : label.right - inset;
                }
            }
        }
        for (int side = LEFT; side <= RIGHT; side++) {
            int n = gather(labels, side, group, false);
            spread(group, n, top, bottom, COLUMN_SPACE * dp, false);
            for (int k = 0; k < n; k++) {
                Label label = group[k];
                label.top = label.mid - label.height / 2;
                label.bottom = label.mid + label.height / 2;
                float gap = (END_GAP + (label.next ? PILL_X : 0)) * dp;
                if (side == RIGHT) {
                    label.left = x + r + COLUMN_GAP * dp;
                    label.right = label.left + label.width;
                    label.endX = label.left - gap;
                } else {
                    label.right = x - r - COLUMN_GAP * dp;
                    label.left = label.right - label.width;
                    label.endX = label.right + gap;
                }
                // Level when it can, or to the label's far end where level would graze the dial
                float inset = Math.min(LEADER_INSET * dp, label.height / 2);
                label.endY = clamp(label.elbowY, label.top + inset, label.bottom - inset);
                if (segmentToPoint(label.elbowX, label.elbowY, label.endX, label.endY, x, y) < r + RIM_GAP * dp) {
                    label.endY = label.elbowY < y ? label.top + inset : label.bottom - inset;
                }
            }
        }
    }

    /**
     * The labels in one row or column, each starting level with its elbow, in the order their windows come
     * round the dial. That keeps the leaders from crossing: a window below the middle whose name goes in
     * the top row has its leader run up the outside, so its name goes at that end of the row.
     */
    private static int gather(Label[] labels, int slot, Label[] group, boolean across) {
        int n = 0;
        for (Label label : labels) {
            if (label.slot != slot) continue;
            label.mid = across ? label.elbowX : label.elbowY;
            int k = n++;
            while (k > 0 && order(group[k - 1], slot) > order(label, slot)) {
                group[k] = group[k - 1];
                k--;
            }
            group[k] = label;
        }
        return n;
    }

    /** Where a label comes along its row, left to right, or its column, top to bottom */
    private static float order(Label label, int slot) {
        switch (slot) {
            case TOP:
                return label.theta >= 180 ? label.theta - 360 : label.theta; // Clockwise over the top
            case RIGHT:
                return label.theta; // Clockwise down the right
            default:
                return -label.theta; // Anticlockwise along the bottom, and up the left read downwards
        }
    }

    /** Pushes the labels apart until none overlap, keeping them inside the box as far as they'll go */
    private static void spread(Label[] group, int n, float start, float end, float space, boolean across) {
        for (int k = 1; k < n; k++) {
            group[k].mid = Math.max(group[k].mid, group[k - 1].mid + (half(group[k - 1], across) + space + half(group[k], across)));
        }
        if (n > 0 && group[n - 1].mid + half(group[n - 1], across) > end) {
            group[n - 1].mid = end - half(group[n - 1], across);
            for (int k = n - 2; k >= 0; k--) {
                group[k].mid = Math.min(group[k].mid, group[k + 1].mid - (half(group[k + 1], across) + space + half(group[k], across)));
            }
        }
        if (n > 0 && group[0].mid - half(group[0], across) < start) {
            group[0].mid = start + half(group[0], across);
            for (int k = 1; k < n; k++) {
                group[k].mid = Math.max(group[k].mid, group[k - 1].mid + (half(group[k - 1], across) + space + half(group[k], across)));
            }
        }
    }

    private static float half(Label label, boolean across) {
        return (across ? label.width : label.height) / 2;
    }

    /**
     * Every label inside the box and clear of the dial and of the others, with room for the highlight round
     * any of them; no leader across the dial, across another label or across another leader
     */
    private boolean isClean(Label[] labels, float left, float top, float right, float bottom) {
        float slack = 0.5f;
        if (centreX - radius < left - slack || centreX + radius > right + slack
                || centreY - radius < top - slack || centreY + radius > bottom + slack) {
            return false;
        }
        float px = PILL_X * dp, py = PILL_Y * dp;
        for (Label label : labels) {
            if (label.left < left - slack || label.right > right + slack || label.top < top - slack || label.bottom > bottom + slack) {
                return false;
            }
            if (rectToPoint(label.left - px, label.top - py, label.right + px, label.bottom + py, centreX, centreY) < radius + 1.5f * dp) {
                return false;
            }
        }
        for (Label a : labels) {
            for (Label b : labels) {
                if (a != b && a.left - px - dp < b.right && b.left < a.right + px + dp && a.top - py - dp < b.bottom && b.top < a.bottom + py + dp) {
                    return false;
                }
            }
        }
        for (Label label : labels) {
            if (segmentToPoint(label.elbowX, label.elbowY, label.endX, label.endY, centreX, centreY) < radius + RIM_GAP * dp - slack) {
                return false;
            }
            for (Label other : labels) {
                if (other == label) continue;
                float l = other.left - px, t = other.top - py, r = other.right + px, b = other.bottom + py;
                if (segmentHitsRect(label.rimX, label.rimY, label.elbowX, label.elbowY, l, t, r, b)
                        || segmentHitsRect(label.elbowX, label.elbowY, label.endX, label.endY, l, t, r, b)) {
                    return false;
                }
                if (crosses(label.rimX, label.rimY, label.elbowX, label.elbowY, other)
                        || crosses(label.elbowX, label.elbowY, label.endX, label.endY, other)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean crosses(float x0, float y0, float x1, float y1, Label other) {
        return segmentsCross(x0, y0, x1, y1, other.rimX, other.rimY, other.elbowX, other.elbowY)
                || segmentsCross(x0, y0, x1, y1, other.elbowX, other.elbowY, other.endX, other.endY);
    }

    private static float leaderLength(Label[] labels) {
        float length = 0;
        for (Label label : labels) {
            length += (float) (Math.hypot(label.elbowX - label.rimX, label.elbowY - label.rimY)
                    + Math.hypot(label.endX - label.elbowX, label.endY - label.elbowY));
        }
        return length;
    }

    // Plane geometry

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static float rectToPoint(float left, float top, float right, float bottom, float x, float y) {
        float dx = Math.max(Math.max(left - x, 0), x - right);
        float dy = Math.max(Math.max(top - y, 0), y - bottom);
        return (float) Math.hypot(dx, dy);
    }

    static float segmentToPoint(float x0, float y0, float x1, float y1, float x, float y) {
        float dx = x1 - x0, dy = y1 - y0;
        float lengthSquared = dx * dx + dy * dy;
        float t = lengthSquared == 0 ? 0 : clamp(((x - x0) * dx + (y - y0) * dy) / lengthSquared, 0, 1);
        return (float) Math.hypot(x0 + t * dx - x, y0 + t * dy - y);
    }

    /** Whether two segments cross each other properly, not just touch */
    static boolean segmentsCross(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        float d1 = orient(cx, cy, dx, dy, ax, ay), d2 = orient(cx, cy, dx, dy, bx, by);
        float d3 = orient(ax, ay, bx, by, cx, cy), d4 = orient(ax, ay, bx, by, dx, dy);
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    private static float orient(float ax, float ay, float bx, float by, float cx, float cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    static boolean segmentHitsRect(float x0, float y0, float x1, float y1, float left, float top, float right, float bottom) {
        if (x0 >= left && x0 <= right && y0 >= top && y0 <= bottom || x1 >= left && x1 <= right && y1 >= top && y1 <= bottom) {
            return true;
        }
        return segmentsCross(x0, y0, x1, y1, left, top, right, top) || segmentsCross(x0, y0, x1, y1, right, top, right, bottom)
                || segmentsCross(x0, y0, x1, y1, right, bottom, left, bottom) || segmentsCross(x0, y0, x1, y1, left, bottom, left, top);
    }
}
