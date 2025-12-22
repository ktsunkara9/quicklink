package inc.skt.quicklink.repository;

import inc.skt.quicklink.model.UrlMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB implementation of UrlRepository using Enhanced Client.
 * Provides CRUD operations for URL mappings in DynamoDB.
 */
@Repository
@Profile("prod")
public class DynamoDbUrlRepository implements UrlRepository {
    
    private final DynamoDbTable<UrlMapping> table;
    
    public DynamoDbUrlRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${dynamodb.table.urls:quicklink-urls}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(UrlMapping.class));
    }
    
    @Override
    public UrlMapping save(UrlMapping urlMapping) {
        table.putItem(urlMapping);
        return urlMapping;
    }
    
    @Override
    public Optional<UrlMapping> findByShortCode(String shortCode) {
        Key key = Key.builder().partitionValue(shortCode).build();
        return Optional.ofNullable(table.getItem(key));
    }
    
    @Override
    public boolean existsByShortCode(String shortCode) {
        return findByShortCode(shortCode).isPresent();
    }
    
    @Override
    public void softDelete(String shortCode) {
        UrlMapping existing = findByShortCode(shortCode)
                .orElseThrow(() -> new RuntimeException("URL not found: " + shortCode));
        
        existing.setIsActive(false);
        table.updateItem(existing);
    }
    
    @Override
    public void updateExpiry(String shortCode, Long expiresAt) {
        UrlMapping existing = findByShortCode(shortCode)
                .orElseThrow(() -> new RuntimeException("URL not found: " + shortCode));
        
        existing.setExpiresAt(expiresAt);
        table.updateItem(existing);
    }
}
