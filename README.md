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

## Quick Setup

For detailed setup instructions and troubleshooting, see the [**User Guide**](USER-GUIDE.md).

A single script handles everything — prerequisites check, Docker infrastructure (Hadoop + ActiveMQ), Maven build, and OpenDaylight download/configuration.

```bash
# Full setup
./setup.sh
```

| Option | Description |
|---|---|
| `./setup.sh` | Full setup (infra + build + ODL) |
| `./setup.sh --infra-only` | Start Docker services only |
| `./setup.sh --build-only` | Build CHIEF only |
| `./setup.sh --stop` | Stop and remove all containers |
| `./setup.sh --clean` | Full reset (stops services, wipes cache, and rebuilds) |

After the script finishes, start OpenDaylight and install the CHIEF features:

```bash
# Source environment (Java 8)
source ./env.sh

# Start Karaf
./distribution-karaf-0.3.4-Lithium-SR4/bin/karaf
```

```karaf
# Inside the Karaf console
feature:repo-add mvn:org.opendaylight.chief/chief-features/1.0-SNAPSHOT/xml/features
feature:install odl-chief-all
```

## Service Endpoints

| Service | URL | Credentials |
|---|---|---|
| ActiveMQ Broker | `tcp://localhost:61616` | — |
| ActiveMQ Web Console | http://localhost:8161 | admin / admin |
| Hadoop NameNode UI | http://localhost:9870 | — |
| Hadoop HDFS | `hdfs://localhost:9000` | — |

## Configuration Reference

### Messaging4Transport
Generated automatically by `setup.sh` at `<karaf>/etc/org.opendaylight.messaging4transport.cfg`:
```properties
brokerUrl=tcp://localhost:61616
username=admin
password=admin
```

### CHIEF Orchestration (MD-SAL)
```xml
<chief-config xmlns="urn:opendaylight:params:xml:ns:yang:chief">
    <controller-id>chief-node-1</controller-id>
    <broker-url>tcp://localhost:61616</broker-url>
</chief-config>
```

### Hadoop HDFS
```properties
hdfs.url=hdfs://localhost:9000
```

## Citing CHIEF

If you use CHIEF in your research, please cite the below paper:

* Kathiravelu, P. and Veiga, L., 2016, April. **CHIEF: controller farm for clouds of software-defined community networks.** In 2016 IEEE International Conference on Cloud Engineering Workshop (IC2EW) (pp. 1-6). IEEE.