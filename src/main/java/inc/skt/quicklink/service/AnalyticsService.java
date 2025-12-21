package inc.skt.quicklink.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import inc.skt.quicklink.dto.AnalyticsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Service for recording analytics events asynchronously.
 * Uses @Async to avoid blocking redirect responses.
 */
@Service
public class AnalyticsService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);
    
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;
    
    public AnalyticsService(
            SqsClient sqsClient,
            @Value("${aws.sqs.analytics-queue-url:}") String queueUrl,
            ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Records a click event.
     * Fire-and-forget pattern - errors are logged but don't affect redirect.
     */
    public void recordClick(String shortCode, String ipAddress, String userAgent) {
        try {
            AnalyticsEvent event = new AnalyticsEvent(
                    shortCode,
                    System.currentTimeMillis() / 1000,
                    ipAddress,
                    userAgent
            );
            
            String messageBody = objectMapper.writeValueAsString(event);
            
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();
            
            sqsClient.sendMessage(request);
            logger.debug("Analytics event sent to SQS for shortCode: {}", shortCode);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize analytics event: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to send analytics to SQS: {}", e.getMessage());
        }
    }
}
