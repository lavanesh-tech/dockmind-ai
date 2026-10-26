#!/usr/bin/env bash
#
# Check the Terraform code without touching AWS: formatting, then `terraform validate`
# (syntax, types, references). Runs Terraform in Docker, so nothing needs installing.
# No AWS credentials are used or needed; providers are downloaded from the Terraform registry.
set -euo pipefail
cd "$(dirname "$0")/../infra/terraform"

TF="docker run --rm -v $PWD:/work -w /work -e TF_IN_AUTOMATION=1 hashicorp/terraform:1.16.6"
$TF fmt -check -recursive
$TF init -backend=false -input=false -no-color >/dev/null
$TF validate -no-color
rm -rf .terraform .terraform.lock.hcl
