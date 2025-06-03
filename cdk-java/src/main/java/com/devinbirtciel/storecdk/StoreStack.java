package com.devinbirtciel.storecdk;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.IntegrationOptions;

import java.util.Map;

public class StoreStack extends Stack {
    public StoreStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public StoreStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Create DynamoDB table for products
        Table productTable = Table.Builder.create(this, "ProductTable")
                .tableName("products-table")
                .partitionKey(software.amazon.awscdk.services.dynamodb.Attribute.builder()
                        .name("id")
                        .type(AttributeType.STRING)
                        .build())
                .removalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY) // For dev/demo only
                .build();

        // Path to your built Spring Boot Lambda JAR (adjust as needed)
        String lambdaJar = "../build/libs/store-0.0.1-SNAPSHOT.jar";

        Function productLambda = Function.Builder.create(this, "ProductLambda")
                .runtime(Runtime.JAVA_21)
                .handler("org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest")
                .code(Code.fromAsset(lambdaJar))
                .memorySize(2048)
                .timeout(Duration.seconds(30))
                .environment(Map.of(
                        "AWS_REGION", Stack.of(this).getRegion(),
                        "PRODUCT_TABLE_NAME", productTable.getTableName()
                ))
                .build();

        // Grant Lambda permissions to access the DynamoDB table
        productTable.grantReadWriteData(productLambda);

        // Create API Gateway REST API with explicit resources and methods
        RestApi api = RestApi.Builder.create(this, "ProductRestApi")
                .restApiName("Product Service")
                .deployOptions(software.amazon.awscdk.services.apigateway.StageOptions.builder().stageName("prod").build())
                .build();

        // /products resource
        Resource products = api.getRoot().addResource("products");
        // /products/{id} resource
        Resource productById = products.addResource("{id}");

        // LambdaIntegration with functionName query param for each handler
        LambdaIntegration getAllProductsIntegration = LambdaIntegration.Builder.create(productLambda)
                .requestParameters(Map.of("integration.request.querystring.functionName", "'getAllProducts'"))
                .build();
        LambdaIntegration addProductIntegration = LambdaIntegration.Builder.create(productLambda)
                .requestParameters(Map.of("integration.request.querystring.functionName", "'addProduct'"))
                .build();
        LambdaIntegration getProductIntegration = LambdaIntegration.Builder.create(productLambda)
                .requestParameters(Map.of("integration.request.querystring.functionName", "'getProduct'"))
                .build();
        LambdaIntegration updateProductIntegration = LambdaIntegration.Builder.create(productLambda)
                .requestParameters(Map.of("integration.request.querystring.functionName", "'updateProduct'"))
                .build();
        LambdaIntegration removeProductIntegration = LambdaIntegration.Builder.create(productLambda)
                .requestParameters(Map.of("integration.request.querystring.functionName", "'removeProduct'"))
                .build();

        // Map HTTP methods to Lambda handlers
        products.addMethod("GET", getAllProductsIntegration);
        products.addMethod("POST", addProductIntegration);
        productById.addMethod("GET", getProductIntegration);
        productById.addMethod("PUT", updateProductIntegration);
        productById.addMethod("DELETE", removeProductIntegration);

        CfnOutput.Builder.create(this, "ApiUrl")
                .value(api.getUrl())
                .build();
        CfnOutput.Builder.create(this, "ProductTableName")
                .value(productTable.getTableName())
                .build();
    }
}

