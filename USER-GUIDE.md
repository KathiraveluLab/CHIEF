# CHIEF User Guide: Setup & Troubleshooting

This guide provides detailed instructions for setting up the CHIEF framework and resolving common issues encountered when running legacy OpenDaylight code in modern environments.

---

## 1. Environment Prerequisite: Java 8

CHIEF is built on OpenDaylight Lithium, which **requires Java 8 (1.8.x)**. Running on Java 11, 17, or 21 will cause compilation failures and runtime JVM errors (e.g., `Unrecognized VM option 'UnsyncloadClass'`).

### Automated Setup
The `./setup.sh` script automatically:
1.  Detects if Java 8 is installed.
2.  Installs `openjdk-8-jdk` if missing.
3.  Generates an `env.sh` file to manage your paths.

### Synchronizing your Shell
Every time you open a new terminal to work with CHIEF, you **must** source the environment:
```bash
source ./env.sh
```
This ensures `JAVA_HOME` points to Java 8 and `PATH` includes the correct Java binaries.

---

## 2. Quick Start Workflow

1.  **Full Setup**:
    ```bash
    ./setup.sh
    ```
    This pulls Docker images (Hadoop/ActiveMQ), builds all Maven modules, and configures OpenDaylight.

2.  **Initialize Console Environment**:
    ```bash
    source ./env.sh
    ```

3.  **Launch OpenDaylight**:
    ```bash
    ./distribution-karaf-0.3.4-Lithium-SR4/bin/karaf
    ```

4.  **Deploy CHIEF Features**:
    Inside the Karaf console:
    ```karaf
    feature:repo-add mvn:org.opendaylight.chief/chief-features/1.0-SNAPSHOT/xml/features
    feature:install odl-chief-all
    ```

---

## 3. Infrastructure (Docker)

CHIEF relies on a distributed infrastructure provided via Docker Compose:
- **ActiveMQ (AMQP 1.0)**: Used for inter-cloud event forwarding via Messaging4Transport.
- **Hadoop 3.2.1**: Used by the `chief-billing` module for parallel processing of traffic stats.

**Management Commands:**
- Start: `./setup.sh --infra-only`
- Stop: `./setup.sh --stop`
- Monitor Hadoop: [http://localhost:9870](http://localhost:9870)
- Monitor ActiveMQ: [http://localhost:8161](http://localhost:8161) (admin/admin)

---

## 4. Troubleshooting Guide

### Issue: `Unrecognized VM option 'UnsyncloadClass'`
- **Cause**: You are running Karaf with a Java version newer than 8.
- **Fix**: Run `source ./env.sh` to switch to Java 8. If you absolutely must use a newer Java, the `setup.sh` script applies a patch to `bin/karaf` to remove this flag, but OSGi stability is not guaranteed.

### Issue: `Error resolving artifact ... Could not find artifact`
- **Cause**: Karaf's Maven resolver is looking at its internal `system/` directory instead of your local `~/.m2/repository`.
- **Fix**: The `setup.sh` script automatically patches `etc/org.ops4j.pax.url.mvn.cfg`. If you still face this, ensure `org.ops4j.pax.url.mvn.localRepository` in that file points to `${user.home}/.m2/repository`.

### Issue: `Bundle has already been installed`
- **Cause**: A conflict in the OSGi runtime from a previous `feature:install` attempt.
- **Fix**: Perform a clean restart of Karaf:
  ```bash
  ./distribution-karaf-0.3.4-Lithium-SR4/bin/karaf clean
  ```
  Then re-add the repo and install the feature.

### Issue: `class file has wrong version 61.0, should be 52.0`
- **Cause**: A dependency (often Hadoop) was compiled for a newer Java version than the one you are currently using for build (Java 8).
- **Fix**: Ensure your `pom.xml` uses Hadoop versions that support Java 8 (e.g., `3.2.x` or `3.3.x`). CHIEF is configured to use **Hadoop 3.2.1**.

### Issue: `Return code is: 501, ReasonPhrase: Not Implemented`
- **Cause**: Maven Central and many other repositories have disabled insecure HTTP access. Legacy OpenDaylight (Karaf 3.x) is pre-configured with `http://` URLs.
- **Fix**: Update `etc/org.ops4j.pax.url.mvn.cfg` to use `https://` for all URLs. The `setup.sh` script now does this automatically.

### Issue: `JAXBException` or `NoClassDefFoundError: javax/xml/bind/...`
- **Cause**: JAXB was removed from the JDK in Java 11.
- **Fix**: Use Java 8. If you are forced to build on Java 11+, you must manually add JAXB dependencies to your `pom.xml` or plugin configurations.

---

## 5. Module Reference

- **chief-api**: YANG models and API definitions.
- **chief-impl**: Core Inter-Cloud Orchestrator logic.
- **chief-monitoring**: ODL-based traffic statistics collector.
- **chief-billing**: Hadoop-based metering and billing service.
- **chief-features**: Karaf feature definitions for deployment.
