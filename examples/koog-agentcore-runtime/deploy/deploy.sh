#!/usr/bin/env bash
# Create one IAM role and one AgentCore Runtime, IAM-authorized.
# Outputs are written to deploy/runtime-info.json for the test script to consume.
#
# A single runtime serves both response variants: streaming vs non-streaming is chosen
# per request via the `Accept` header (see App.kt), not per deployment.
set -euo pipefail
cd "$(dirname "$0")/.."

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=${AWS_REGION:-us-east-1}
IMAGE_URI=$(cat deploy/image-uri.txt)

ROLE_NAME=KoogAgentCoreExampleRole
RUNTIME_NAME=koog_agentcore_example

echo ">>> Account: ${ACCOUNT_ID}  Region: ${REGION}"
echo ">>> Image:   ${IMAGE_URI}"

# --- IAM role ---------------------------------------------------------------
TRUST=$(cat <<JSON
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "bedrock-agentcore.amazonaws.com" },
    "Action": "sts:AssumeRole",
    "Condition": {
      "StringEquals": { "aws:SourceAccount": "${ACCOUNT_ID}" },
      "ArnLike":      { "aws:SourceArn": "arn:aws:bedrock-agentcore:${REGION}:${ACCOUNT_ID}:*" }
    }
  }]
}
JSON
)

POLICY=$(cat <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow",
      "Action": ["ecr:BatchGetImage","ecr:GetDownloadUrlForLayer","ecr:GetAuthorizationToken"],
      "Resource": "*" },
    { "Effect": "Allow",
      "Action": ["logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents","logs:DescribeLogStreams","logs:DescribeLogGroups"],
      "Resource": "arn:aws:logs:${REGION}:${ACCOUNT_ID}:*" },
    { "Effect": "Allow",
      "Action": ["bedrock:InvokeModel","bedrock:InvokeModelWithResponseStream"],
      "Resource": "*" }
  ]
}
JSON
)

if ! aws iam get-role --role-name "${ROLE_NAME}" >/dev/null 2>&1; then
  echo ">>> Creating IAM role ${ROLE_NAME}"
  aws iam create-role --role-name "${ROLE_NAME}" \
    --assume-role-policy-document "${TRUST}" >/dev/null
else
  echo ">>> Updating trust policy on existing role ${ROLE_NAME}"
  aws iam update-assume-role-policy --role-name "${ROLE_NAME}" \
    --policy-document "${TRUST}" >/dev/null
fi

aws iam put-role-policy --role-name "${ROLE_NAME}" \
  --policy-name AgentCoreExecutionPolicy \
  --policy-document "${POLICY}" >/dev/null
ROLE_ARN=$(aws iam get-role --role-name "${ROLE_NAME}" --query Role.Arn --output text)
echo ">>> Role: ${ROLE_ARN}"

# Allow the role to propagate
sleep 8

# --- Create or update the agent runtime ------------------------------------
echo ">>> Upserting runtime ${RUNTIME_NAME}"
EXISTING_ID=$(aws bedrock-agentcore-control list-agent-runtimes --region "${REGION}" \
  --query "agentRuntimes[?agentRuntimeName=='${RUNTIME_NAME}'].agentRuntimeId | [0]" --output text 2>/dev/null || echo "None")

PAYLOAD=$(cat <<JSON
{
  "agentRuntimeArtifact": { "containerConfiguration": { "containerUri": "${IMAGE_URI}" } },
  "networkConfiguration":  { "networkMode": "PUBLIC" },
  "roleArn": "${ROLE_ARN}"
}
JSON
)

if [[ "${EXISTING_ID}" == "None" || -z "${EXISTING_ID}" ]]; then
  aws bedrock-agentcore-control create-agent-runtime --region "${REGION}" \
    --agent-runtime-name "${RUNTIME_NAME}" \
    --cli-input-json "${PAYLOAD}" >/dev/null
else
  aws bedrock-agentcore-control update-agent-runtime --region "${REGION}" \
    --agent-runtime-id "${EXISTING_ID}" \
    --cli-input-json "${PAYLOAD}" >/dev/null
fi

# --- Wait for READY ---------------------------------------------------------
for i in $(seq 1 60); do
  STATUS=$(aws bedrock-agentcore-control list-agent-runtimes --region "${REGION}" \
    --query "agentRuntimes[?agentRuntimeName=='${RUNTIME_NAME}'].status | [0]" --output text 2>/dev/null || true)
  case "${STATUS}" in
    READY)   echo ">>> ${RUNTIME_NAME}: READY"; break ;;
    FAILED|UPDATE_FAILED|CREATE_FAILED)
             echo "!!! ${RUNTIME_NAME}: ${STATUS}"; exit 1 ;;
    *)       echo "    ${RUNTIME_NAME}: ${STATUS:-?} (${i}/60)"; sleep 5 ;;
  esac
done

# --- Capture ARN ------------------------------------------------------------
ARN=$(aws bedrock-agentcore-control list-agent-runtimes --region "${REGION}" \
  --query "agentRuntimes[?agentRuntimeName=='${RUNTIME_NAME}'].agentRuntimeArn | [0]" --output text)

cat > deploy/runtime-info.json <<JSON
{
  "region": "${REGION}",
  "runtime": { "name": "${RUNTIME_NAME}", "arn": "${ARN}" }
}
JSON

echo
echo ">>> Deployed: ${ARN}"
echo "    info written to deploy/runtime-info.json"
