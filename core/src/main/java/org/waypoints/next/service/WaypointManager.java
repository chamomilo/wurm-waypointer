package org.waypoints.next.service;

import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.UUID;

/** Thread-safe in-memory CRUD boundary; persistence is an explicit caller concern. */
public final class WaypointManager {
    private final WaypointRecordValidator validator;
    private final List<WaypointRecord> records = new ArrayList<WaypointRecord>();
    private long revision;

    public WaypointManager(WaypointRecordValidator validator) {
        if (validator == null) throw new IllegalArgumentException("validator is required");
        this.validator = validator;
    }

    public synchronized void replaceAll(List<WaypointRecord> values) {
        validator.validateAll(values);
        records.clear();
        records.addAll(values);
        revision++;
    }

    public synchronized WaypointRecord add(WaypointRecord record) {
        validator.validate(record);
        if (indexOf(record.getId()) >= 0) {
            throw new IllegalArgumentException("waypoint id already exists: " + record.getId());
        }
        records.add(record);
        revision++;
        return record;
    }

    public synchronized WaypointRecord update(WaypointRecord record) {
        validator.validate(record);
        int index = indexOf(record.getId());
        if (index < 0) throw new IllegalArgumentException("waypoint does not exist: " + record.getId());
        records.set(index, record);
        revision++;
        return record;
    }

    public synchronized WaypointRecord duplicate(UUID id, Instant now) {
        WaypointRecord source = require(id);
        String suffix = " Copy";
        String name = source.getName();
        int maximumBase = Math.max(1, org.waypoints.next.validation.WaypointLimits.MAX_NAME
                - suffix.length());
        if (name.length() > maximumBase) name = name.substring(0, maximumBase);
        WaypointRecord copy = source.duplicate(name + suffix, now);
        validator.validate(copy);
        records.add(copy);
        revision++;
        return copy;
    }

    public synchronized WaypointRecord setEnabled(UUID id, boolean enabled, Instant now) {
        WaypointRecord changed = require(id).withEnabled(enabled, now);
        return update(changed);
    }

    public synchronized boolean delete(UUID id) {
        int index = indexOf(id);
        if (index < 0) return false;
        records.remove(index);
        revision++;
        return true;
    }

    /** Removes every due temporary waypoint in one revision change. */
    public synchronized List<WaypointRecord> removeExpired(long nowEpochMillis) {
        List<WaypointRecord> removed = new ArrayList<WaypointRecord>();
        for (Iterator<WaypointRecord> values = records.iterator(); values.hasNext();) {
            WaypointRecord record = values.next();
            if (org.waypoints.next.model.WaypointLifetime.isExpired(
                    record.getExpiresAt(), nowEpochMillis)) {
                removed.add(record);
                values.remove();
            }
        }
        if (!removed.isEmpty()) revision++;
        return Collections.unmodifiableList(removed);
    }

    /** Long.MAX_VALUE means that no temporary waypoint is scheduled. */
    public synchronized long nextExpiryEpochMilli() {
        long next = Long.MAX_VALUE;
        for (WaypointRecord record : records) {
            if (record.getExpiresAt() != null) {
                next = Math.min(next, record.getExpiresAt().toEpochMilli());
            }
        }
        return next;
    }

    public synchronized WaypointRecord find(UUID id) {
        int index = indexOf(id);
        return index < 0 ? null : records.get(index);
    }

    public synchronized WaypointRecord findByIdOrExactName(String value) {
        if (value == null) return null;
        String clean = value.trim();
        try {
            WaypointRecord byId = find(UUID.fromString(clean));
            if (byId != null) return byId;
        } catch (IllegalArgumentException ignored) {
            // Continue with the exact-name lookup.
        }
        WaypointRecord match = null;
        for (WaypointRecord record : records) if (record.getName().equalsIgnoreCase(clean)) {
            if (match != null) throw new IllegalArgumentException(
                    "waypoint name is ambiguous; use its UUID");
            match = record;
        }
        return match;
    }

    public synchronized List<WaypointRecord> snapshot() {
        return Collections.unmodifiableList(new ArrayList<WaypointRecord>(records));
    }

    /** Atomically pairs a record snapshot with the revision that produced it. */
    public synchronized WaypointRevisionSnapshot revisionSnapshot() {
        return new WaypointRevisionSnapshot(revision, records);
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized List<WaypointRecord> filtered(WaypointFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter is required");
        List<WaypointRecord> result = new ArrayList<WaypointRecord>();
        for (WaypointRecord record : records) if (filter.matches(record)) result.add(record);
        return Collections.unmodifiableList(result);
    }

    private WaypointRecord require(UUID id) {
        WaypointRecord record = find(id);
        if (record == null) throw new IllegalArgumentException("waypoint does not exist: " + id);
        return record;
    }

    private int indexOf(UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < records.size(); i++) {
            if (id.equals(records.get(i).getId())) return i;
        }
        return -1;
    }
}
