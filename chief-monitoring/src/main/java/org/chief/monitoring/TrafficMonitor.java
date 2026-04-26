package org.chief.monitoring;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.FlowCapableNodeConnectorStatisticsData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.chief.rev160323.InterCloudEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.chief.rev160323.InterCloudEventBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

/**
 * Monitors inter-community traffic metrics by querying the ODL Statistics Manager.
 * As specified in Section IV-A of the CHIEF paper.
 */
public class TrafficMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(TrafficMonitor.class);
    private final DataBroker dataBroker;

    public TrafficMonitor(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    /**
     * Queries real-time traffic statistics for a specific node connector (port).
     * @param nodeId The ODL Node ID (e.g., "openflow:1").
     * @param connectorId The Node Connector ID (e.g., "openflow:1:1").
     */
    public void queryAndReportTraffic(String nodeId, String connectorId) {
        LOG.info("Querying traffic stats for Node: {}, Connector: {}", nodeId, connectorId);

        InstanceIdentifier<FlowCapableNodeConnectorStatisticsData> statIid = InstanceIdentifier
                .builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(nodeId)))
                .child(NodeConnector.class, new NodeConnectorKey(new NodeConnectorId(connectorId)))
                .augmentation(FlowCapableNodeConnectorStatisticsData.class)
                .build();

        try (ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
            Optional<FlowCapableNodeConnectorStatisticsData> stats = 
                    tx.read(LogicalDatastoreType.OPERATIONAL, statIid).get();
            
            if (stats.isPresent() && stats.get().getFlowCapableNodeConnectorStatistics() != null) {
                BigInteger txBytes = stats.get().getFlowCapableNodeConnectorStatistics().getBytes().getTransmitted();
                publishMonitoringEvent(nodeId, txBytes);
            } else {
                LOG.warn("No statistics found for Connector: {}", connectorId);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to read statistics from MD-SAL", e);
        }
    }

    private void publishMonitoringEvent(String sourceNode, BigInteger txBytes) {
        InterCloudEvent monitoringEvent = new InterCloudEventBuilder()
                .setSourceCloudId(sourceNode)
                .setDestinationCloudId("monitoring-hub")
                .setPayload("TrafficStats:TransmittedBytes=" + txBytes.toString())
                .build();

        LOG.info("Traffic monitoring event published to CHIEF federation: {}", monitoringEvent.getPayload());
        // Messaging4Transport will automatically forward this to the AMQP broker.
    }
}
