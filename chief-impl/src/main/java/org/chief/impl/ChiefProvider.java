package org.chief.impl;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.chief.rev160323.ChiefService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.chief.rev160323.InterCloudEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.chief.rev160323.RequestResourceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.chief.rev160323.RequestResourceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.chief.rev160323.RequestResourceOutputBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

public class ChiefProvider implements BindingAwareProvider, ChiefService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ChiefProvider.class);

    private NotificationProviderService notificationService;
    private InterCloudOrchestrator orchestrator;
    private ResourceAllocationService resourceService;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("CHIEF Provider Session Initiated");
        notificationService = session.getSALService(NotificationProviderService.class);
        orchestrator = new InterCloudOrchestrator("chief-cloud-1", notificationService);
        resourceService = new ResourceAllocationService("chief-cloud-1");

        // Register for inter-cloud notifications (Simulated listener for research parity)
        session.addNotificationListener(InterCloudEvent.class, notification -> {
            orchestrator.handleRemoteEvent(notification);
        });
    }

    @Override
    public void close() throws Exception {
        LOG.info("CHIEF Provider Closed");
    }

    @Override
    public Future<RpcResult<RequestResourceOutput>> requestResource(RequestResourceInput input) {
        LOG.info("Resource requested from cloud: " + input.getTargetCloudId());
        
        // Federation logic via InterCloudOrchestrator
        orchestrator.routeEvent(input.getTargetCloudId(), "ResourceRequest:" + input.getResourceType());
        
        RequestResourceOutput result = new RequestResourceOutputBuilder()
                .setStatus("Request forwarded to " + input.getTargetCloudId())
                .build();
        
        return RpcResultBuilder.success(result).buildFuture();
    }
}
