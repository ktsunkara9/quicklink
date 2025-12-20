#!/usr/bin/env python3
import aws_cdk as cdk
from quicklink_stack import QuickLinkStack

print("[DEBUG] Starting CDK app synthesis...")

app = cdk.App()

QuickLinkStack(app, "QuickLinkStack",
    env=cdk.Environment(
        account=app.node.try_get_context("account"),
        region=app.node.try_get_context("region") or "us-east-1"
    )
)

print("[DEBUG] Calling app.synth()...")
app.synth()
print("[DEBUG] Synthesis complete!")
