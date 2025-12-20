"""QuickLink CDK Stack - Defines AWS infrastructure for URL shortener."""
from aws_cdk import (
    Stack,
    RemovalPolicy,
    Duration,
    aws_dynamodb as dynamodb,
    aws_lambda as lambda_,
    aws_apigateway as apigateway,
)
from constructs import Construct

class QuickLinkStack(Stack):

    def __init__(self, scope: Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        # DynamoDB Table: quicklink-urls
        self.urls_table = dynamodb.Table(
            self, "UrlsTable",
            table_name="quicklink-urls",
            partition_key=dynamodb.Attribute(
                name="shortCode",
                type=dynamodb.AttributeType.STRING
            ),
            billing_mode=dynamodb.BillingMode.PAY_PER_REQUEST,
            removal_policy=RemovalPolicy.DESTROY,
            time_to_live_attribute="expiresAt"
        )

        # DynamoDB Table: quicklink-tokens
        self.tokens_table = dynamodb.Table(
            self, "TokensTable",
            table_name="quicklink-tokens",
            partition_key=dynamodb.Attribute(
                name="tokenId",
                type=dynamodb.AttributeType.STRING
            ),
            billing_mode=dynamodb.BillingMode.PAY_PER_REQUEST,
            removal_policy=RemovalPolicy.DESTROY
        )

        # Lambda Function: QuickLink Spring Boot Application
        self.quicklink_function = lambda_.Function(
            self, "QuickLinkFunction",
            function_name="quicklink-service",
            runtime=lambda_.Runtime.JAVA_17,
            handler="inc.skt.quicklink.StreamLambdaHandler::handleRequest",
            code=lambda_.Code.from_asset("../target/quicklink-1.0.0.jar"),
            memory_size=512,  # Minimum for Spring Boot
            timeout=Duration.seconds(10),  # Sufficient for cold start + execution
            environment={
                "AWS_REGION": "us-east-1",
                "DYNAMODB_TABLE_URLS": self.urls_table.table_name,
                "DYNAMODB_TABLE_TOKENS": self.tokens_table.table_name
            }
        )

        # Grant Lambda permissions to access DynamoDB tables
        self.urls_table.grant_read_write_data(self.quicklink_function)
        self.tokens_table.grant_read_write_data(self.quicklink_function)

        # API Gateway: REST API
        self.api = apigateway.LambdaRestApi(
            self, "QuickLinkApi",
            rest_api_name="quicklink-api",
            handler=self.quicklink_function,
            proxy=True,
            deploy_options=apigateway.StageOptions(
                stage_name="prod",
                throttling_rate_limit=100,
                throttling_burst_limit=200
            )
        )
