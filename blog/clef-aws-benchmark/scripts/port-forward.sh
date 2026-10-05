#!/usr/bin/env bash
# Forward a machine's llama-server (127.0.0.1:8080 on the instance) to localhost:<port> over SSM.
# No inbound security-group rule, SSH key or public endpoint involved.
#
# Usage: scripts/port-forward.sh g6-xlarge [local_port]
#   then: python3 bench/quickstart.py --url http://127.0.0.1:8080
set -euo pipefail
cd "$(dirname "$0")/../terraform"

name="${1:?usage: $0 <machine> [local_port]}"
port="${2:-8080}"
id="$(terraform output -json machines | python3 -c "import json,sys; print(json.load(sys.stdin)['${name}']['id'])")"
region="$(terraform output -raw region)"

aws ssm start-session --region "${region}" --target "${id}" \
  --document-name AWS-StartPortForwardingSession \
  --parameters "portNumber=8080,localPortNumber=${port}"
