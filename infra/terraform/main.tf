data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name = "dockmind-${var.environment}"
  azs  = slice(data.aws_availability_zones.available.names, 0, 2)

  # Internal DNS names (AWS Cloud Map) the services use to reach each other.
  ns = "dockmind.local"

  # Every application container, its port and how it is reached.
  #   public: behind the load balancer   internal: only from other services
  apps = {
    "api-gateway"                 = { port = 8080, cpu = 512, memory = 1024, health = "/actuator/health" }
    "shipment-service"            = { port = 8081, cpu = 512, memory = 1024, health = "/actuator/health" }
    "dock-scheduling-service"     = { port = 8082, cpu = 512, memory = 1024, health = "/actuator/health" }
    "disruption-recovery-service" = { port = 8083, cpu = 512, memory = 1024, health = "/actuator/health" }
    "ai-operations-service"       = { port = 8084, cpu = 512, memory = 1024, health = "/actuator/health" }
    "keycloak"                    = { port = 8080, cpu = 512, memory = 1024, health = "/realms/dockmind" }
    "kafka"                       = { port = 9092, cpu = 512, memory = 1024, health = "" }
    "frontend"                    = { port = 8080, cpu = 256, memory = 512, health = "/healthz" }
  }

  # Images built from this repository and pushed to ECR (Kafka uses the public image).
  built_images = toset(["api-gateway", "shipment-service", "dock-scheduling-service",
  "disruption-recovery-service", "ai-operations-service", "keycloak", "frontend"])

  public_url = "http://${aws_lb.app.dns_name}"
  issuer     = "${local.public_url}/realms/dockmind"
  jwks       = "http://keycloak.${local.ns}:8080/realms/dockmind/protocol/openid-connect/certs"
  kafka      = "kafka.${local.ns}:9092"

  # One PostgreSQL instance, one schema per service: each service still owns its tables and
  # migrations, and can't see the others' (separate schemas, own Flyway history).
  jdbc = "jdbc:postgresql://${aws_db_instance.main.address}:5432/dockmind"
}
