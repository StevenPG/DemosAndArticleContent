package com.stevenpg.defaults;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/** The retained positions for one aircraft, oldest first, capped at {@code capacity}. */
public final class Track {

    private final long aircraftId;
    private final String callsign;
    private final int capacity;
    private final ArrayDeque<Position> positions;

    public Track(long aircraftId, String callsign, int capacity) {
        this.aircraftId = aircraftId;
        this.callsign = callsign;
        this.capacity = capacity;
        this.positions = new ArrayDeque<>(capacity);
    }

    public long aircraftId() {
        return aircraftId;
    }

    public String callsign() {
        return callsign;
    }

    public synchronized void append(Position position) {
        if (positions.size() == capacity) {
            positions.pollFirst();
        }
        positions.addLast(position);
    }

    public synchronized List<Position> latest(int limit) {
        int n = Math.min(limit, positions.size());
        List<Position> out = new ArrayList<>(n);
        Iterator<Position> newestFirst = positions.descendingIterator();
        for (int i = 0; i < n; i++) {
            out.add(newestFirst.next());
        }
        return out;
    }

    public synchronized void forEach(Consumer<Position> action) {
        positions.forEach(action);
    }

    public synchronized int size() {
        return positions.size();
    }
}
