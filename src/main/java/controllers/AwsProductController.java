package controllers;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import entities.Product;
import exceptions.ProductNotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
public class AwsProductController {

        private final DynamoDbClient dynamoDb;
        private final String tableName;

        public AwsProductController() {
            this.dynamoDb = DynamoDbClient.builder()
                    .region(Region.of(System.getenv("AWS_REGION")))
                    .build();
            this.tableName = System.getenv("PRODUCT_TABLE_NAME");
        }

        @Bean
        public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> getProduct() {
            return event -> {
                try {
                    Map<String, String> pathParams = event.getPathParameters();
                    if (pathParams == null || !pathParams.containsKey("id")) {
                        return createResponse(400, "Missing product id");
                    }
                    String productId = pathParams.get("id");

                    GetItemRequest request = GetItemRequest.builder()
                            .tableName(tableName)
                            .key(Map.of("id", AttributeValue.builder().s(productId).build()))
                            .build();

                    GetItemResponse response = dynamoDb.getItem(request);

                    if (response.item() == null || response.item().isEmpty()) {
                        throw new ProductNotFoundException(productId);
                    }

                    Map<String, AttributeValue> item = response.item();
                    Product product = mapToProduct(item);

                    return createResponse(200, convertToJson(product));
                } catch (ProductNotFoundException e) {
                    return createResponse(404, e.getMessage());
                } catch (Exception e) {
                    return createResponse(500, "Internal server error: " + e.getMessage());
                }
            };
        }

        @Bean
        public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> getAllProducts() {
            return event -> {
                try {
                    ScanRequest scanRequest = ScanRequest.builder()
                            .tableName(tableName)
                            .build();

                    ScanResponse scanResponse = dynamoDb.scan(scanRequest);
                    List<Product> products = scanResponse.items().stream()
                            .map(this::mapToProduct)
                            .toList();

                    CollectionModel<EntityModel<Product>> productModels = CollectionModel.of(
                            products.stream()
                                    .map(EntityModel::of)
                                    .collect(Collectors.toList()));

                    return createResponse(200, convertToJson(productModels));
                } catch (Exception e) {
                    return createResponse(500, "Internal server error: " + e.getMessage());
                }
            };
        }

        @Bean
        public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> createProduct() {
            return event -> {
                try {
                    Product newProduct = parseProduct(event.getBody());

                    PutItemRequest putRequest = PutItemRequest.builder()
                            .tableName(tableName)
                            .item(mapToAttributeValues(newProduct))
                            .build();

                    dynamoDb.putItem(putRequest);

                    EntityModel<Product> entityModel = EntityModel.of(newProduct);

                    return createResponse(201, convertToJson(entityModel));
                } catch (Exception e) {
                    return createResponse(500, "Internal server error: " + e.getMessage());
                }
            };
        }

        @Bean
        public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> removeProduct() {
            return event -> {
                try {
                    Map<String, String> pathParams = event.getPathParameters();
                    if (pathParams == null || !pathParams.containsKey("id")) {
                        return createResponse(400, "Missing product id");
                    }
                    String productId = pathParams.get("id");

                    DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                            .tableName(tableName)
                            .key(Map.of("id", AttributeValue.builder().s(productId).build()))
                            .build();
                    dynamoDb.deleteItem(deleteRequest);
                    return createResponse(204, "");
                } catch (Exception e) {
                    return createResponse(500, "Internal server error: " + e.getMessage());
                }
            };
        }

        @Bean
        public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> addProduct() {
            return event -> {
                try {
                    Product newProduct = parseProduct(event.getBody());
                    PutItemRequest putRequest = PutItemRequest.builder()
                            .tableName(tableName)
                            .item(mapToAttributeValues(newProduct))
                            .build();
                    dynamoDb.putItem(putRequest);
                    EntityModel<Product> entityModel = EntityModel.of(newProduct);
                    return createResponse(201, convertToJson(entityModel));
                } catch (Exception e) {
                    return createResponse(500, "Internal server error: " + e.getMessage());
                }
            };
        }

        @Bean
        public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> updateProduct() {
            return event -> {
                try {
                    Map<String, String> pathParams = event.getPathParameters();
                    if (pathParams == null || !pathParams.containsKey("id")) {
                        return createResponse(400, "Missing product id");
                    }
                    String productId = pathParams.get("id");
                    Product updatedProduct = parseProduct(event.getBody());
                    Map<String, AttributeValue> key = Map.of("id", AttributeValue.builder().s(productId).build());
                    Map<String, String> expressionAttributeNames = new HashMap<>();
                    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
                    StringBuilder updateExpression = new StringBuilder("set ");
                    boolean first = true;
                    if (updatedProduct.getName() != null && !updatedProduct.getName().isBlank()) {
                        if (!first) updateExpression.append(", ");
                        expressionAttributeNames.put("#name", "name");
                        expressionAttributeValues.put(":name", AttributeValue.builder().s(updatedProduct.getName()).build());
                        updateExpression.append("#name = :name");
                        first = false;
                    }
                    if (updatedProduct.getPrice() != 0.0f) {
                        if (!first) updateExpression.append(", ");
                        expressionAttributeNames.put("#price", "price");
                        expressionAttributeValues.put(":price", AttributeValue.builder().n(String.valueOf(updatedProduct.getPrice())).build());
                        updateExpression.append("#price = :price");
                        first = false;
                    }
                    if (expressionAttributeNames.isEmpty()) {
                        return createResponse(400, "No valid fields to update");
                    }
                    UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                            .tableName(tableName)
                            .key(key)
                            .updateExpression(updateExpression.toString())
                            .expressionAttributeNames(expressionAttributeNames)
                            .expressionAttributeValues(expressionAttributeValues)
                            .returnValues("ALL_NEW")
                            .build();
                    UpdateItemResponse updateResponse = dynamoDb.updateItem(updateRequest);
                    Product product = mapToProduct(updateResponse.attributes());
                    EntityModel<Product> entityModel = EntityModel.of(product);
                    return createResponse(200, convertToJson(entityModel));
                } catch (Exception e) {
                    return createResponse(500, "Internal server error: " + e.getMessage());
                }
            };
        }

        // Utility: Convert object to JSON
        private String convertToJson(Object obj) {
            return new Gson().toJson(obj);
        }

        // Utility: Parse Product from JSON
        private Product parseProduct(String json) {
            return new Gson().fromJson(json, Product.class);
        }

        // Utility: Create API Gateway Proxy Response
        private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
            return new APIGatewayProxyResponseEvent().withStatusCode(statusCode).withBody(body);
        }

        // Fix: mapToProduct and mapToAttributeValues
        private Product mapToProduct(Map<String, AttributeValue> item) {
            Product product = new Product();
            if (item.get("id") != null) {
                try {
                    product.setId(Long.parseLong(item.get("id").s()));
                } catch (NumberFormatException e) {
                    product.setId(null);
                }
            }
            if (item.get("name") != null) {
                product.setName(item.get("name").s());
            }
            if (item.get("price") != null) {
                try {
                    product.setPrice(Float.parseFloat(item.get("price").n()));
                } catch (NumberFormatException e) {
                    product.setPrice(0.0f);
                }
            }
            return product;
        }

        private Map<String, AttributeValue> mapToAttributeValues(Product product) {
            Map<String, AttributeValue> attributes = new HashMap<>();
            attributes.put("id", AttributeValue.builder().s(String.valueOf(product.getId())).build());
            attributes.put("name", AttributeValue.builder().s(product.getName()).build());
            attributes.put("price", AttributeValue.builder().n(String.valueOf(product.getPrice())).build());
            return attributes;
        }
}
