#!/bin/bash
# Primo argomento del CLI Picocli è il comando radice `webrobot` (vedi WebRobotCliCommand).
exec java -jar ./target/org.webrobot.eu.spark.job-0.3-uber.jar webrobot "$@"