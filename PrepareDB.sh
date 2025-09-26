#!/bin/bash
./WebRobotCli.sh project add --name="testProject" --description="test-project"
/WebRobotCli.sh bot add --projectId=ae0d26cd-e180-4d9c-bb85-a7fc7182badc --name="testBot" --description="testBot" --codeFile="./code.bot"
