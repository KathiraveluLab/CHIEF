package org.chief.billing;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * MapReduce application to analyze traffic metrics.
 * Operates on HDFS decoupled from the active SDN workflow, as described
 * in the SDS 2016 CHIEF paper (Section IV-B & Figure 4).
 */
public class TrafficAnalyticsJob {

    private static final Logger LOG = LoggerFactory.getLogger(TrafficAnalyticsJob.class);

    /**
     * Mapper extracts the tenant ID from the HDFS file path and parses the transmitted bytes.
     */
    public static class TrafficMapper extends Mapper<Object, Text, Text, LongWritable> {
        
        private final Text tenantId = new Text();
        private final LongWritable bytesTransmitted = new LongWritable();

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            // value is the payload line, e.g., "TrafficStats:TransmittedBytes=5000"
            String payload = value.toString();
            
            if (payload.startsWith("TrafficStats:TransmittedBytes=")) {
                String[] parts = payload.split("=");
                if (parts.length == 2) {
                    try {
                        long txBytes = Long.parseLong(parts[1].trim());
                        bytesTransmitted.set(txBytes);

                        // File path is /chief/billing/{tenantId}/data.txt
                        Path filePath = ((FileSplit) context.getInputSplit()).getPath();
                        String tenant = filePath.getParent().getName();
                        tenantId.set(tenant);

                        context.write(tenantId, bytesTransmitted);
                    } catch (NumberFormatException e) {
                        LOG.warn("Could not parse TransmittedBytes from payload: {}", payload);
                    }
                }
            }
        }
    }

    /**
     * Reducer aggregates traffic bytes per tenant and checks against policy thresholds.
     */
    public static class TrafficReducer extends Reducer<Text, LongWritable, Text, LongWritable> {

        private final LongWritable totalBytesW = new LongWritable();
        private static final long THROTTLE_THRESHOLD = 50_000_000L; // 50 MB threshold mock

        @Override
        public void reduce(Text key, Iterable<LongWritable> values, Context context) 
                throws IOException, InterruptedException {
            
            long totalBytes = 0;
            for (LongWritable val : values) {
                totalBytes += val.get();
            }
            
            totalBytesW.set(totalBytes);
            context.write(key, totalBytesW);

            LOG.info("Tenant {} consumed a total of {} bytes", key.toString(), totalBytes);

            // Feed results into Throttling/Billing policies offline.
            if (totalBytes > THROTTLE_THRESHOLD) {
                LOG.warn("Tenant {} has exceeded the throttling threshold of {} bytes. Applying traffic shaping.", 
                        key.toString(), THROTTLE_THRESHOLD);
            }
        }
    }

    /**
     * Configures and executes the Hadoop Job.
     */
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        
        // HDFS URLs would be injected from environment properties, defaulting to localhost.
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        Job job = Job.getInstance(conf, "CHIEF Inter-Cloud Traffic Analytics");
        job.setJarByClass(TrafficAnalyticsJob.class);
        
        job.setMapperClass(TrafficMapper.class);
        job.setCombinerClass(TrafficReducer.class);
        job.setReducerClass(TrafficReducer.class);
        
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(LongWritable.class);
        
        // Scan the entire billing root for tenant subdirectories.
        FileInputFormat.addInputPath(job, new Path("/chief/billing/"));
        
        // Output aggregated results to a dynamically timestamped directory.
        Path outputPath = new Path("/chief/billing-processed/run_" + System.currentTimeMillis());
        FileOutputFormat.setOutputPath(job, outputPath);
        
        LOG.info("Starting TrafficAnalyticsJob MapReduce on HDFS...");
        boolean success = job.waitForCompletion(true);
        LOG.info("TrafficAnalyticsJob MapReduce completed: {}", success);
        
        System.exit(success ? 0 : 1);
    }
}
