package dev.vsharms.clearance.controls;

import dev.vsharms.clearance.config.ClearanceProperties;
import dev.vsharms.clearance.tenant.Tenant;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class AuditRetentionControl implements Control {

    private final int minDays;

    public AuditRetentionControl(ClearanceProperties props) {
        this.minDays = props.minAuditRetentionDays();
    }

    @Override public String id() { return "AU-11"; }
    @Override public String title() { return "Audit record retention"; }

    @Override
    public ControlResult evaluate(Tenant tenant) {
        int days = tenant.getAuditRetentionDays();
        return days >= minDays
                ? ControlResult.pass(id(), "Audit logs kept for " + days + " days")
                : ControlResult.fail(id(), "Audit logs kept for " + days + " days, need at least " + minDays);
    }
}
