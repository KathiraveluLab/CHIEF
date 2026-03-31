package org.chief.impl;

import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.chief.rev160323.InterCloudEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.chief.rev160323.InterCloudEventBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates inter-cloud communication for CHIEF.
 * This component handles the routing of events and resource requests
 * across federated community network clouds.
 */
public class InterCloudOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(InterCloudOrchestrator.class);

    private final String controllerId;
    private final NotificationProviderService notificationService;
    private ResourceAllocationService resourceAllocationService;

    public InterCloudOrchestrator(String controllerId, NotificationProviderService notificationService) {
        this.controllerId = controllerId;
        this.notificationService = notificationService;
    }

    public void setResourceAllocationService(ResourceAllocationService resourceAllocationService) {
        this.resourceAllocationService = resourceAllocationService;
    }

    /**
     * Routes an event to a target cloud for a specific tenant.
     * @param targetCloudId The destination cloud's unique ID.
     * @param tenantId The associated tenant ID.
     * @param payload The event data.
     */
    public void routeEvent(String targetCloudId, String tenantId, String payload) {
        LOG.info("Routing event for tenant {} from {} to {}", tenantId, controllerId, targetCloudId);

        InterCloudEvent event = new InterCloudEventBuilder()
                .setSourceCloudId(controllerId)
                .setDestinationCloudId(targetCloudId)
                .setTenantId(tenantId)
                .setPayload(payload)
                .build();

        // Publish the event to MD-SAL.
        // Messaging4Transport will automatically forward this to the AMQP broker.
        publishToBroker(event);
    }

    /**
     * Handles a remote event received from the federation broker.
     * Implements the receive-side of the Event Propagation Algorithm (Section III-B).
     */
    public void handleRemoteEvent(InterCloudEvent event) {
        LOG.info("Received remote event from {}: Tenant={}, Payload={}", 
                event.getSourceCloudId(), event.getTenantId(), event.getPayload());

        // 1. Parse the event to check if it's a resource request
        if (event.getPayload().startsWith("ResourceRequest:")) {
            processResourceRequest(event);
        }
    }

    private void processResourceRequest(InterCloudEvent event) {
        String[] parts = event.getPayload().split(":");
        String type = parts[1].split("=")[0];
        int amount = Integer.parseInt(parts[1].split("=")[1]);

        LOG.info("Processing federated resource request: Type={}, Amount={}", type, amount);
        
        boolean canAccommodate = false;
        if (resourceAllocationService != null) {
            canAccommodate = resourceAllocationService.handleFederatedRequest(event.getSourceCloudId(), type, amount);
        }
        
        if (canAccommodate) {
            initiateFederation(event.getTenantId(), type, amount);
        } else {
            LOG.warn("Cannot accommodate federated request for {} units of {} from {}", 
                    amount, type, event.getSourceCloudId());
        }
    }

    private void initiateFederation(String tenantId, String type, int amount) {
        LOG.info("Initiating federation provisioning for tenant {}: {} units of {}", 
                tenantId, amount, type);
        // Provisioning logic as per Section III-B execution threads.
    }

    private void publishToBroker(InterCloudEvent event) {
        LOG.debug("Publishing to Messaging4Transport AMQP broker: {}", event.getPayload());
        if (notificationService != null) {
            notificationService.publish(event);
        } else {
            LOG.warn("NotificationProviderService is null. Cannot publish event.");
        }
    }
}
