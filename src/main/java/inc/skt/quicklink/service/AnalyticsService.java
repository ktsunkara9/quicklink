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
     * Records a click event asynchronously.
     * Runs in background thread, does not block redirect response.
     */
    @Async
    public void recordClick(String shortCode, String ipAddress, String userAgent) {
        logger.info("=== ANALYTICS START: shortCode={}, thread={}", shortCode, Thread.currentThread().getName());
        logger.info("=== ANALYTICS: queueUrl={}", queueUrl);
        
        try {
            AnalyticsEvent event = new AnalyticsEvent(
                    shortCode,
                    System.currentTimeMillis() / 1000,
                    ipAddress,
                    userAgent
            );
            
            logger.info("=== ANALYTICS: Event created: {}", event);
            
            String messageBody = objectMapper.writeValueAsString(event);
            logger.info("=== ANALYTICS: JSON serialized: {}", messageBody);
            
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();
            
            logger.info("=== ANALYTICS: Sending to SQS...");
            sqsClient.sendMessage(request);
            logger.info("=== ANALYTICS SUCCESS: Message sent to SQS for shortCode: {}", shortCode);
            
        } catch (JsonProcessingException e) {
            logger.error("=== ANALYTICS ERROR: Failed to serialize: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("=== ANALYTICS ERROR: Failed to send to SQS: {}", e.getMessage(), e);
        }
    }
}
