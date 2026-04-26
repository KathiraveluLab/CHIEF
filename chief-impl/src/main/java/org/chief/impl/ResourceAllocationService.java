package org.chief.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles community network resource allocation logic as referenced in the CHIEF paper [33].
 * This service manages resource sharing decisions within and between federated clouds.
 */
public class ResourceAllocationService {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceAllocationService.class);

    private final String cloudId;
    private final InterCloudOrchestrator orchestrator;
    private final Map<String, Integer> availableResources = new ConcurrentHashMap<String, Integer>();

    public ResourceAllocationService(String cloudId, InterCloudOrchestrator orchestrator) {
        this.cloudId = cloudId;
        this.orchestrator = orchestrator;
    }

    /**
     * Adds capacity to the local resource pool.
     */
    public void addResourceCapacity(String type, int amount) {
        synchronized (availableResources) {
            Integer current = availableResources.get(type);
            int newVal = (current == null) ? amount : current + amount;
            availableResources.put(type, newVal);
            LOG.info("Added {} units of {} to local capacity. Total: {}", 
                     amount, type, newVal);
        }
    }

    /**
     * Allocates resources for a local or federated request.
     * Implements the logic referenced in [33].
     */
    public boolean allocateResource(String requestType, int amount, String tenantId) {
        LOG.info("Processing resource allocation for cloud {}: Tenant={}, Type={}, Amount={}", 
                cloudId, tenantId, requestType, amount);

        boolean success = localAllocation(requestType, amount);
        
        if (!success) {
            LOG.info("Local resources insufficient. Escalating to federated request.");
            if (orchestrator != null) {
                String payload = "ResourceRequest:" + requestType + "=" + amount;
                orchestrator.routeEvent("federation-broker", tenantId, payload);
            }
            return false;
        }
        
        return true;
    }

    /**
     * Handles an incoming federated resource request from another cloud.
     */
    public boolean handleFederatedRequest(String sourceCloudId, String requestType, int amount) {
        LOG.info("Handling federated request from {}: Type={}, Amount={}", 
                sourceCloudId, requestType, amount);
        
        // Check if we have excess capacity to share
        return localAllocation(requestType, amount);
    }

    private boolean localAllocation(String type, int amount) {
        LOG.debug("Verifying local {} availability for {} units", type, amount);
        synchronized (availableResources) {
            Integer current = availableResources.get(type);
            if (current == null || current < amount) {
                return false;
            }
            availableResources.put(type, current - amount);
            return true;
        }
    }
}
