package inc.skt.quicklink.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * DynamoDB implementation of TokenRepository using atomic ADD operation.
 * Provides thread-safe, distributed counter for ID generation.
 */
@Repository
@Profile("prod")
public class DynamoDbTokenRepository implements TokenRepository {
    
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    
    public DynamoDbTokenRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${dynamodb.table.tokens:quicklink-tokens}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }
    
    @Override
    public long incrementAndGet(String tokenId, long incrementBy) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("tokenId", AttributeValue.builder().s(tokenId).build());
        
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":increment", AttributeValue.builder().n(String.valueOf(incrementBy)).build());
        expressionValues.put(":timestamp", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis() / 1000)).build());
        
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("ADD currentRangeEnd :increment SET lastUpdated = :timestamp")
                .expressionAttributeValues(expressionValues)
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();
        
        UpdateItemResponse response = dynamoDbClient.updateItem(request);
        
        return Long.parseLong(response.attributes().get("currentRangeEnd").n());
    }
}
