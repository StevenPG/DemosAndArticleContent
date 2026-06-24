package com.example.actuator.actuator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for every {@link AuditApplicationEvent} (both our custom
 * {@code WIDGET_*} events and the authentication success/failure events Spring
 * Security publishes automatically) and logs them.
 *
 * <p>The same events are stored by the {@code AuditEventRepository} bean and served
 * from {@code /actuator/auditevents}.
 */
@Slf4j
@Component
public class AuditEventLogger {

    @EventListener
    public void on(AuditApplicationEvent event) {
        log.info("AUDIT [{}] principal={} data={}",
                event.getAuditEvent().getType(),
                event.getAuditEvent().getPrincipal(),
                event.getAuditEvent().getData());
    }
}
