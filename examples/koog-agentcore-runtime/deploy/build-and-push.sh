#!/usr/bin/env bash
# Build the fat-jar, build a linux/arm64 Docker image, push to ECR.
# Reuses a single ECR repo "koog-agentcore-example" — no random suffixes.
set -euo pipefail
cd "$(dirname "$0")/.."

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=${AWS_REGION:-us-east-1}
REPO=koog-agentcore-example
IMAGE_URI="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${REPO}:latest"

echo ">>> Account: ${ACCOUNT_ID}  Region: ${REGION}  Repo: ${REPO}"

# Ensure ECR repo exists
if ! aws ecr describe-repositories --repository-names "${REPO}" --region "${REGION}" >/dev/null 2>&1; then
  echo ">>> Creating ECR repository ${REPO}"
  aws ecr create-repository --repository-name "${REPO}" --region "${REGION}" \
    --image-scanning-configuration scanOnPush=true >/dev/null
fi

echo ">>> Logging into ECR"
aws ecr get-login-password --region "${REGION}" | \
  docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com" >/dev/null

echo ">>> Gradle fat-jar"
gradle --no-daemon --console=plain fatJar -q

echo ">>> Docker build (linux/arm64)"
docker build --platform linux/arm64 -t "${REPO}:latest" .
docker tag "${REPO}:latest" "${IMAGE_URI}"

echo ">>> Push"
docker push "${IMAGE_URI}"

echo "${IMAGE_URI}" > deploy/image-uri.txt
echo ">>> Done. Image: ${IMAGE_URI}"
