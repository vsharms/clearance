package dev.vsharms.clearance.tenant;

import dev.vsharms.clearance.audit.AuditEvent;
import dev.vsharms.clearance.audit.AuditService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService service;
    private final AuditService audit;

    public TenantController(TenantService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> submit(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @Valid @RequestBody CreateTenantRequest request,
                                                 @AuthenticationPrincipal Jwt caller) {
        TenantService.Submission result = service.submit(request, idempotencyKey, caller.getSubject());
        TenantResponse body = TenantResponse.from(result.tenant());
        // 201 the first time, 200 when a client retries with the same key
        return result.created()
                ? ResponseEntity.created(URI.create("/api/tenants/" + body.id())).body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> get(@PathVariable UUID id) {
        return service.find(id)
                .map(t -> ResponseEntity.ok(TenantResponse.from(t)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/{id}/audit")
    public List<AuditEvent> auditTrail(@PathVariable UUID id) {
        return audit.trailFor(id);
    }
}
