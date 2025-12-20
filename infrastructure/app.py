#!/usr/bin/env python3
import aws_cdk as cdk
from quicklink_stack import QuickLinkStack
import json
import os

print("[DEBUG] Starting CDK app synthesis...")

app = cdk.App()

stack = QuickLinkStack(app, "QuickLinkStack",
    env=cdk.Environment(
        account=app.node.try_get_context("account"),
        region=app.node.try_get_context("region") or "us-east-1"
    )
)

print("[DEBUG] Calling app.synth()...")
cloud_assembly = app.synth()
print("[DEBUG] Synthesis complete!")

# Manually write CloudFormation template
os.makedirs("cdk.out", exist_ok=True)
for stack_artifact in cloud_assembly.stacks:
    template_file = os.path.join("cdk.out", f"{stack_artifact.stack_name}.template.json")
    with open(template_file, "w") as f:
        json.dump(stack_artifact.template, f, indent=2)
    print(f"[DEBUG] Written template to {template_file}")
