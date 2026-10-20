package dev.vsharms.clearance.controls;

import dev.vsharms.clearance.tenant.Tenant;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class EncryptionAtRestControl implements Control {

    @Override public String id() { return "SC-28"; }
    @Override public String title() { return "Protection of information at rest"; }

    @Override
    public ControlResult evaluate(Tenant tenant) {
        return tenant.isEncryptionAtRest()
                ? ControlResult.pass(id(), "Data at rest is encrypted")
                : ControlResult.fail(id(), "Tenant data stores must be encrypted at rest");
    }
}
