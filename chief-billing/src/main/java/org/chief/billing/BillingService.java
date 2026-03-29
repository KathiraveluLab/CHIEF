package org.chief.billing;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Implements Billing and Metering for CHIEF with Hadoop integration.
 * As described in Section IV-B of the IEEE SDS 2016 paper.
 */
public class BillingService {

    private static final Logger LOG = LoggerFactory.getLogger(BillingService.class);

    /**
     * Records billing data for a tenant and processes it via Hadoop.
     */
    public void recordBillingData(String tenantId, String billingInfo) {
        LOG.info("Recording billing data for tenant {}: {}", tenantId, billingInfo);

        // Integration with Hadoop HDFS for offline parallel processing.
        try {
            uploadToHDFS(tenantId, billingInfo);
        } catch (IOException e) {
            LOG.error("Failed to upload billing data to HDFS", e);
        }
    }

    private void uploadToHDFS(String tenantId, String data) throws IOException {
        Configuration conf = new Configuration();
        // The HDFS URL would be configured in CHIEF environment.
        conf.set("fs.defaultFS", "hdfs://localhost:9000");
        FileSystem fs = FileSystem.get(conf);

        Path hdfsPath = new Path("/chief/billing/" + tenantId + "/data.txt");
        if (fs.exists(hdfsPath)) {
            fs.delete(hdfsPath, true);
        }

        try (OutputStream os = fs.create(hdfsPath)) {
            os.write(data.getBytes());
        }
        LOG.info("Billing data successfully uploaded to HDFS for {} at {}", tenantId, hdfsPath);
    }
}
