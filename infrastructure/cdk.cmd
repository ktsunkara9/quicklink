@echo off
if "%1"=="synth" (
    .venv\Scripts\python.exe app.py
) else (
    echo Use: .\cdk.cmd synth
    echo For deploy/bootstrap, install CDK CLI: npm install -g aws-cdk
)
