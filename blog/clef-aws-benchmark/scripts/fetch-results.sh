#!/usr/bin/env bash
# Copy every machine's results from S3 into ./results/ (run before terraform destroy).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}/terraform"

bucket="$(terraform output -raw results_bucket)"
region="$(terraform output -raw region)"
aws s3 sync --region "${region}" "s3://${bucket}/results/" "${ROOT}/results/"
echo "results in ${ROOT}/results/ - next: python3 bench/analyze.py"
