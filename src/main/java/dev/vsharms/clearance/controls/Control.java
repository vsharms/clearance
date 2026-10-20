package dev.vsharms.clearance.controls;

import dev.vsharms.clearance.tenant.Tenant;

/**
 * One security control a tenant must satisfy before activation.
 * IDs follow NIST SP 800-53 (the catalog FedRAMP baselines are built from), but these are simplified checks.
 */
public interface Control {
    String id();
    String title();
    ControlResult evaluate(Tenant tenant);
}
