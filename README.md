# CHIEF: Controller Farm for Clouds of Software-Defined Community Networks

CHIEF is a cloud-based inter-cloud federation framework designed for Software-Defined Community Networks (SDCN). 

## Features

- **Decentralized Inter-Cloud Federation**: Loose coupling of community network controllers.
- **OpenDaylight Integration**: Built on the MD-SAL architecture.
- **Messaging4Transport Support**: Automatic Northbound binding for MD-SAL using AMQP/JMS.
- **Event-Driven Architecture**: Schema-agnostic notification forwarding and RPC proxying.
- **Network Traffic Monitoring**: Real-time stats query via ODL Statistics Manager (Section IV-A).
- **Hadoop-Based Billing**: Metering and billing service with HDFS integration (Section IV-B).
- **Multi-Tenancy & Throttling**: Support for virtual partitioning and rate-limited inter-cloud traffic.

## Architecture

CHIEF leverages [Messaging4Transport](https://github.com/KathiraveluLab/Messaging4Transport) to achieve loose coupling between federated clouds.

1.  **CHIEF Provider**: Listens for resource requests and orchestrates inter-cloud cycles.
2.  **Inter-Cloud Orchestrator**: Routes events by publishing MD-SAL notifications.
3.  **Monitoring & Billing**: Independent bundles for traffic analysis and Hadoop-based metering.
4.  **Messaging4Transport**: Automatically intercepts these notifications and forwards them to a shared AMQP broker.

## Prerequisites

- **Java 8**
- **Apache Maven 3.3.9+**
- **OpenDaylight (Helium/Lithium/Beryllium recommended)**
- **Apache ActiveMQ (for AMQP 1.0 broker)**
- **Hadoop/HDFS (for Billing service parallel processing)**

## Build and Install

```bash
# Clone and build CHIEF
mvn clean install -DskipTests
```

## Running the Framework

### 1. Start the Hadoop Cluster (Docker)
CHIEF uses Hadoop for offline billing analysis. You can start a local Hadoop cluster using Docker:
```bash
# Start the Hadoop stack
docker-compose up -d
```
The NameNode Web UI will be available at `http://localhost:9870`.

### 2. Start the AMQP Broker (ActiveMQ)
```bash
# Start ActiveMQ
$ACTIVEMQ_HOME/bin/activemq start
```

### 2. Start OpenDaylight
```bash
# Run Karaf
./bin/karaf
```

### 3. Install Features
In the Karaf console, install the CHIEF features:
```karaf
feature:repo-add mvn:org.opendaylight.chief/chief-features/1.0-SNAPSHOT/xml/features
feature:install odl-chief-all
```

## Configuration

### Messaging4Transport Configuration
Configure the AMQP broker URL in `etc/org.opendaylight.messaging4transport.cfg`:
```properties
brokerUrl=tcp://localhost:61616
username=admin
password=admin
```

### CHIEF Orchestration Configuration
The `controller-id` and `tenant` settings are managed via MD-SAL configuration:
```xml
<chief-config xmlns="urn:opendaylight:params:xml:ns:yang:chief">
    <controller-id>chief-node-1</controller-id>
    <broker-url>tcp://localhost:61616</broker-url>
</chief-config>
```

### Hadoop HDFS Configuration
The Billing service connects to HDFS for offline data analysis. Ensure the HDFS endpoint is reachable:
```properties
hdfs.url=hdfs://localhost:9000
```

## Citing CHIEF

If you use CHIEF in your research, please cite the below paper:

* Kathiravelu, P. and Veiga, L., 2016, April. **CHIEF: controller farm for clouds of software-defined community networks.** In 2016 IEEE International Conference on Cloud Engineering Workshop (IC2EW) (pp. 1-6). IEEE.