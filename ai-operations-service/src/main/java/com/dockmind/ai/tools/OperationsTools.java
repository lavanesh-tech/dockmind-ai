package com.dockmind.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The tools the model may call, created per request for one user. Every tool is a read: it
 * returns data and changes nothing. Inputs are validated before any request is made, so the
 * model can't steer a call to an arbitrary path.
 */
public class OperationsTools {

    /** Tool names, kept in one place so tests can check the model is offered exactly these. */
    public static final List<String> NAMES = List.of("getSchedule", "listDocks", "listDisruptions", "getDisruption",
            "getDisruptionAudit");

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final ReadOnlyBackend backend;
    private final String token;
    private final List<String> used = Collections.synchronizedList(new ArrayList<>());

    public OperationsTools(ReadOnlyBackend backend, String token) {
        this.backend = backend;
        this.token = token;
    }

    /** Tool names in the order the model called them. */
    public List<String> used() {
        return List.copyOf(used);
    }

    @Tool(description = "Get the latest computed dock schedule for one day: every appointment (truck, dock, start, end), "
            + "trucks that could not be scheduled and why, and the average waiting time.")
    public String getSchedule(@ToolParam(description = "The day, as YYYY-MM-DD") String date) {
        used.add("getSchedule");
        if (!isDate(date)) {
            return "INVALID_INPUT: date must be YYYY-MM-DD.";
        }
        return backend.get(ReadOnlyBackend.Service.DOCK_SCHEDULING, "/api/v1/schedules/{date}", token, date);
    }

    @Tool(description = "List the warehouse's loading docks with their capabilities and opening hours.")
    public String listDocks() {
        used.add("listDocks");
        return backend.get(ReadOnlyBackend.Service.DOCK_SCHEDULING, "/api/v1/docks", token);
    }

    @Tool(description = "List the most recent disruptions (late trucks, dock outages) with their status and recovery plans.")
    public String listDisruptions() {
        used.add("listDisruptions");
        return backend.get(ReadOnlyBackend.Service.DISRUPTION_RECOVERY, "/api/v1/disruptions", token);
    }

    @Tool(description = "Get one disruption: what happened, the recovery plans compared (appointments moved, trucks "
            + "unscheduled, waiting time), which is recommended, and what was decided.")
    public String getDisruption(@ToolParam(description = "The disruption id (a UUID)") String disruptionId) {
        used.add("getDisruption");
        if (!isUuid(disruptionId)) {
            return "INVALID_INPUT: disruptionId must be a UUID.";
        }
        return backend.get(ReadOnlyBackend.Service.DISRUPTION_RECOVERY, "/api/v1/disruptions/{id}", token, disruptionId);
    }

    @Tool(description = "Get the audit trail of one disruption: who reported, approved or rejected it, and when.")
    public String getDisruptionAudit(@ToolParam(description = "The disruption id (a UUID)") String disruptionId) {
        used.add("getDisruptionAudit");
        if (!isUuid(disruptionId)) {
            return "INVALID_INPUT: disruptionId must be a UUID.";
        }
        return backend.get(ReadOnlyBackend.Service.DISRUPTION_RECOVERY, "/api/v1/disruptions/{id}/audit", token,
                disruptionId);
    }

    private static boolean isDate(String value) {
        if (value == null || !DATE.matcher(value).matches()) {
            return false;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
