# Secrets live in AWS Secrets Manager and are injected into containers at start-up.
# Nothing secret is in Git or in the task definitions. Generated values never leave AWS.

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "random_password" "demo_user" {
  length  = 20
  special = false
}

resource "random_password" "smoke_client" {
  length  = 32
  special = false
}

resource "random_password" "keycloak_admin" {
  length  = 24
  special = false
}

locals {
  generated_secrets = {
    "db-password"             = random_password.db.result
    "demo-user-password"      = random_password.demo_user.result
    "smoke-client-secret"     = random_password.smoke_client.result
    "keycloak-admin-password" = random_password.keycloak_admin.result
  }
}

resource "aws_secretsmanager_secret" "generated" {
  for_each                = local.generated_secrets
  name                    = "${local.name}/${each.key}"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "generated" {
  for_each      = local.generated_secrets
  secret_id     = aws_secretsmanager_secret.generated[each.key].id
  secret_string = each.value
}

# The OpenAI key is set by you, never by Terraform:
#   aws secretsmanager put-secret-value --secret-id dockmind-dev/openai-api-key --secret-string '<key>'
resource "aws_secretsmanager_secret" "openai" {
  name                    = "${local.name}/openai-api-key"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "openai_placeholder" {
  secret_id     = aws_secretsmanager_secret.openai.id
  secret_string = "not-set"

  lifecycle {
    ignore_changes = [secret_string]
  }
}
