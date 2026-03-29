package org.chief.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements throttling for inter-community traffic in CHIEF.
 * As described in the paper's multi-tenancy and management sections.
 */
public class ThrottlingService {

    private static final Logger LOG = LoggerFactory.getLogger(ThrottlingService.class);

    private final Map<String, Long> tenantThrottlingRates = new HashMap<>();

    /**
     * Configures a throttling rate for a specific tenant.
     * @param tenantId The tenant's unique ID.
     * @param rate The maximum bandwidth in bps.
     */
    public void setThrottleRate(String tenantId, long rate) {
        LOG.info("Configuring throttle rate for tenant {}: {} bps", tenantId, rate);
        tenantThrottlingRates.put(tenantId, rate);
    }

    /**
     * Checks and applies throttling if necessary.
     */
    public boolean checkThrottling(String tenantId, long currentUsage) {
        Long maxRate = tenantThrottlingRates.get(tenantId);
        if (maxRate != null && currentUsage > maxRate) {
            LOG.warn("Throttling active for tenant {}: Usage {} exceeds limit {}", 
                    tenantId, currentUsage, maxRate);
            return true; // Throttling triggered
        }
        return false;
    }
}
