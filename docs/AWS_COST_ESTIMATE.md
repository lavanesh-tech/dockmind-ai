# AWS cost estimate (dev environment)

**Nothing has been created in AWS.** This estimate is for `infra/terraform` as written, running
24/7 in `us-east-1` with on-demand prices. Prices change: check them in the
[AWS Pricing Calculator](https://calculator.aws/) before approving. Data transfer, request-based
charges and taxes are not included; for a demo with little traffic they are small.

Rates used (us-east-1, on-demand, as published in 2026; verify before use):
Fargate Linux/ARM $0.03238 per vCPU-hour and $0.00356 per GB-hour; public IPv4 $0.005 per address-hour;
Application Load Balancer $0.0225 per hour plus LCUs. RDS and ElastiCache rates are approximate list
prices for the smallest Graviton instances.

| Item | Size | Approx. per month |
|---|---|---|
| Fargate: 7 containers (5 services, Keycloak, Kafka) | 0.5 vCPU, 1 GB each | ~$101 |
| Fargate: web app (nginx) | 0.25 vCPU, 0.5 GB | ~$7 |
| Public IPv4 addresses (8 containers + load balancer) | ~10 addresses | ~$37 |
| Application Load Balancer | 1, low traffic (~1 LCU) | ~$22 |
| RDS PostgreSQL 17 | db.t4g.micro, 20 GB gp3, single-AZ | ~$14 |
| ElastiCache Redis | cache.t4g.micro | ~$12 |
| Secrets Manager | 5 secrets | ~$2 |
| CloudWatch Logs, ECR storage | 14-day log retention, ≤10 images per repo | ~$3–5 |
| **Total, running all the time** | | **~$200/month (~$0.27/hour)** |

## Ways to spend less
- **Pause:** `desired_count = 0` stops all containers and their IP addresses: about **$50/month**
  remains (load balancer, database, Redis, secrets). Set it back to 1 to resume; data is kept.
- **Run only for a demo:** create, show, then `terraform destroy`. Everything is removed and costs
  stop. A few hours of use costs about $0.27 per hour.
- **AWS Budgets:** set a monthly budget alert (e.g. $25) in the Billing console before deploying.
- Free-tier or credits on a new account may cover part of this; this estimate assumes none.

Sources: [AWS Fargate pricing](https://aws.amazon.com/fargate/pricing/),
[Fargate rates summary (CloudBurn, 2026)](https://cloudburn.io/blog/aws-fargate-pricing),
[Amazon ECS pricing](https://aws.amazon.com/ecs/pricing/).
