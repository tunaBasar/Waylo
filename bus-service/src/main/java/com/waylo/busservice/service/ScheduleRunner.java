package com.waylo.busservice.service;

import com.waylo.busservice.client.AuthGateway;
import com.waylo.busservice.dto.BusDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Non-transactional facade: saves a schedule first (own transaction), then generates its trips (own transaction).
 * A failing schedule is logged with its id and the run continues.
 */
@Component
public class ScheduleRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleRunner.class);

    private final ScheduleService service;
    private final AuthGateway auth;

    public ScheduleRunner(ScheduleService service, AuthGateway auth) {
        this.service = service;
        this.auth = auth;
    }

    public ScheduleResult create(UUID userId, UUID orgId, ScheduleRequest req) {
        UUID id = service.create(userId, orgId, req);
        return applyAndView(userId, orgId, id);
    }

    public ScheduleResult update(UUID userId, UUID orgId, UUID id, ScheduleRequest req) {
        service.update(userId, orgId, id, req);
        return applyAndView(userId, orgId, id);
    }

    public ScheduleResult activate(UUID userId, UUID orgId, UUID id) {
        service.setActive(userId, orgId, id, true);
        return applyAndView(userId, orgId, id);
    }

    public ScheduleOnly deactivate(UUID userId, UUID orgId, UUID id) {
        service.setActive(userId, orgId, id, false);
        return new ScheduleOnly(service.get(userId, orgId, id));
    }

    /** "Apply now" for one organization. */
    public ScheduleSummary applyOrganization(UUID userId, UUID orgId) {
        auth.requireBusManager(userId, orgId);
        return applyAll(orgId);
    }

    /** Daily job (orgId null = every organization, SERVICE mode). */
    public ScheduleSummary applyAll(UUID orgId) {
        ScheduleSummary total = ScheduleSummary.ZERO;
        List<UUID> ids = service.activeScheduleIds(orgId);
        for (UUID id : ids) {
            try {
                total = total.plus(service.applyOne(id));
            } catch (RuntimeException ex) {
                log.error("Trip schedule {} generation failed", id, ex);
            }
        }
        return total;
    }

    private ScheduleResult applyAndView(UUID userId, UUID orgId, UUID id) {
        ScheduleSummary summary = service.applyOne(id);
        return new ScheduleResult(service.get(userId, orgId, id), summary);
    }
}
