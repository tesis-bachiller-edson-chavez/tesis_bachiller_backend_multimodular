#!/usr/bin/env bash
# Datadog Agent Installation Script for Amazon Linux 2023
# This script runs after deployment completes

set -e

echo "Starting Datadog agent installation check..."

# Do not run on every deployment, only if the agent is not installed.
if [ -f /etc/datadog-agent/datadog.yaml ]; then
  echo "Datadog agent already installed. Skipping installation."
  exit 0
fi

# Get the API Key from the Elastic Beanstalk environment variables
DD_API_KEY=$(/opt/elasticbeanstalk/bin/get-config environment -k DD_API_KEY)

if [ -z "$DD_API_KEY" ]; then
  echo "DD_API_KEY environment variable not set. Skipping Datadog agent installation."
  exit 0
fi

echo "Installing Datadog agent..."
DD_SITE="us5.datadoghq.com" DD_API_KEY=$DD_API_KEY bash -c "$(curl -L https://s3.amazonaws.com/dd-agent/scripts/install_script_agent7.sh)"

# Enable logs collection
echo "Configuring Datadog agent for logs..."
sed -i 's/# logs_enabled: false/logs_enabled: true/' /etc/datadog-agent/datadog.yaml

# Enable APM non-local traffic to accept traces from Docker containers
echo "Enabling APM non-local traffic..."
echo "apm_config:" >> /etc/datadog-agent/datadog.yaml
echo "  apm_non_local_traffic: true" >> /etc/datadog-agent/datadog.yaml

# Enable automatic log collection from all containers
echo "Enabling automatic log collection from containers..."
echo "logs_config:" >> /etc/datadog-agent/datadog.yaml
echo "  container_collect_all: true" >> /etc/datadog-agent/datadog.yaml

# Configure Docker log collection
mkdir -p /etc/datadog-agent/conf.d/docker.d
cat > /etc/datadog-agent/conf.d/docker.d/conf.yaml <<EOF
logs:
  - type: docker
    service: tesis-backend
    source: java
EOF

# Get environment variables for tags
DD_ENV=$(/opt/elasticbeanstalk/bin/get-config environment -k DD_ENV)
DD_SERVICE=$(/opt/elasticbeanstalk/bin/get-config environment -k DD_SERVICE)

# Add tags to datadog.yaml
if [ -n "$DD_ENV" ]; then
  echo "env: $DD_ENV" >> /etc/datadog-agent/datadog.yaml
fi

if [ -n "$DD_SERVICE" ]; then
  echo "tags:" >> /etc/datadog-agent/datadog.yaml
  echo "  - service:$DD_SERVICE" >> /etc/datadog-agent/datadog.yaml
  echo "  - env:$DD_ENV" >> /etc/datadog-agent/datadog.yaml
fi

# Restart the Datadog agent
echo "Restarting Datadog agent..."
systemctl restart datadog-agent

echo "Datadog agent installation and configuration completed successfully."