output "app_url" {
  description = "Open this in a browser once the services are healthy."
  value       = local.public_url
}

output "ecr_repositories" {
  description = "Where the pipeline pushes images."
  value       = { for k, r in aws_ecr_repository.app : k => r.repository_url }
}

output "artifacts_bucket" {
  value = aws_s3_bucket.artifacts.bucket
}

output "demo_password_secret" {
  description = "Read the demo users' password with: aws secretsmanager get-secret-value --secret-id <this>"
  value       = aws_secretsmanager_secret.generated["demo-user-password"].name
}
