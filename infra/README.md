# Deploying DockMind to AWS

`infra/terraform` describes the whole environment. **Nothing here runs against AWS unless you run
it with your AWS credentials.** `./scripts/validate-infra.sh` (also part of `ship.sh` and the
pipeline) only checks the code.

## What gets created

```
Internet ──► Application Load Balancer (HTTP :80)
               ├─ /api/*                 ──► api-gateway ──► shipment / dock-scheduling /
               │                                             disruption-recovery / ai-operations
               ├─ /realms/*, /resources/* ──► Keycloak (login; admin console not exposed)
               └─ everything else          ──► web app (nginx)
ECS Fargate (arm64) in public subnets, reachable only from the load balancer and each other
Cloud Map private DNS (*.dockmind.local) for service-to-service calls
RDS PostgreSQL 17 (private subnets): one instance, one schema per service
ElastiCache Redis (private subnets): gateway rate limits
Kafka: single-node container (dev-grade; MSK would cost far more)
Secrets Manager: database password, demo password, Keycloak admin, smoke-test secret, OpenAI key
ECR: one repository per image (immutable tags, scan on push)
S3: private, encrypted, versioned bucket for artifacts and results
CloudWatch Logs: one log group per container, 14-day retention
```

Cost: see [docs/AWS_COST_ESTIMATE.md](../docs/AWS_COST_ESTIMATE.md), about $200/month running, about
$50/month paused, $0 after `terraform destroy`.

## Known limits of this dev environment
- **HTTP only.** HTTPS needs a domain name and an ACM certificate; add an HTTPS listener before
  real use. Tokens over plain HTTP are only acceptable for a short-lived demo.
- Keycloak runs in dev mode (its own in-container database; the realm is re-imported on every start,
  so sessions are lost on restart). Production: `start` mode on RDS.
- Kafka is one container without persistent storage; Amazon MSK for production.
- One database instance, single AZ; Multi-AZ for production.
- Ansible isn't used: containers and Terraform cover all configuration, so there's nothing for it to do.

## Steps (only when you decide to deploy)
1. Create a budget alert in AWS Billing.
2. Create an IAM user or role for deployments; configure the AWS CLI (`aws configure`).
3. `cd infra/terraform && cp terraform.tfvars.example terraform.tfvars`, set `image_tag`.
4. `terraform init && terraform apply -target=aws_ecr_repository.app` (repositories only, ~free).
5. Log in to ECR and push images: `./scripts/build-images.sh <tag> <account>.dkr.ecr.<region>.amazonaws.com`.
6. `terraform plan`, read it, then `terraform apply`.
7. Put your OpenAI key in Secrets Manager (see `secrets.tf`), then open the `app_url` output.
8. When done: `terraform destroy`.

Or run the Jenkins pipeline with `PUBLISH_IMAGES` and `DEPLOY` ticked; it stops for approval before applying.
