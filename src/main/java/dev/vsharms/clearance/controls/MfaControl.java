package dev.vsharms.clearance.controls;

import dev.vsharms.clearance.tenant.Tenant;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class MfaControl implements Control {

    @Override public String id() { return "IA-2(1)"; }
    @Override public String title() { return "Multi-factor authentication for privileged accounts"; }

    @Override
    public ControlResult evaluate(Tenant tenant) {
        return tenant.isMfaEnforced()
                ? ControlResult.pass(id(), "MFA is enforced")
                : ControlResult.fail(id(), "Tenant must enforce MFA before activation");
    }
}
