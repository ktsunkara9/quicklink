package inc.skt.quicklink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * SQS configuration for AWS SDK v2.
 * Creates SQS client bean for analytics messaging.
 */
@Configuration
public class SqsConfig {
    
    @Value("${aws.region:us-east-1}")
    private String awsRegion;
    
    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
            .region(Region.of(awsRegion))
            .build();
    }
}
