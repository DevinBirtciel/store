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

import java.util.Map;

public class StoreStack extends Stack {
    public StoreStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public StoreStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

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
                        "PRODUCT_TABLE_NAME", "your-dynamodb-table-name" // update as needed
                ))
                .build();

        LambdaRestApi api = LambdaRestApi.Builder.create(this, "ProductApi")
                .handler(productLambda)
                .proxy(true)
                .deployOptions(software.amazon.awscdk.services.apigateway.StageOptions.builder()
                        .stageName("prod")
                        .build())
                .build();

        CfnOutput.Builder.create(this, "ApiUrl")
                .value(api.getUrl())
                .build();
    }
}

