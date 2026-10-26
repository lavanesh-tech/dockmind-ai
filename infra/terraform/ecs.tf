resource "aws_ecs_cluster" "main" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "disabled" # enable for per-task metrics in CloudWatch (extra cost)
  }
}

resource "aws_service_discovery_private_dns_namespace" "main" {
  name = local.ns
  vpc  = aws_vpc.main.id
}

resource "aws_service_discovery_service" "app" {
  for_each = local.apps
  name     = each.key
  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.main.id
    routing_policy = "MULTIVALUE"
    dns_records {
      type = "A"
      ttl  = 10
    }
  }
}

resource "aws_cloudwatch_log_group" "app" {
  for_each          = local.apps
  name              = "/ecs/${local.name}/${each.key}"
  retention_in_days = 14
}

# --- IAM: the execution role pulls images, writes logs and reads only DockMind's secrets.
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${local.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "read_secrets" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = concat([for s in aws_secretsmanager_secret.generated : s.arn],
    [aws_secretsmanager_secret.openai.arn])
  }
}

resource "aws_iam_role_policy" "read_secrets" {
  name   = "read-dockmind-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.read_secrets.json
}

# The containers themselves get a role with no AWS permissions at all.
resource "aws_iam_role" "task" {
  name               = "${local.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

# --- What each container needs.
locals {
  secret = { for k, s in aws_secretsmanager_secret.generated : k => s.arn }

  java_common = {
    OIDC_ISSUER_URI                                       = local.issuer
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI = local.jwks
    TRACING_SAMPLING_PROBABILITY                          = "0.1"
  }

  db_env = {
    for svc, schema in {
      "shipment-service"            = "shipment"
      "dock-scheduling-service"     = "dock"
      "disruption-recovery-service" = "recovery"
      } : svc => {
      DB_URL                                         = "${local.jdbc}?currentSchema=${schema}"
      DB_USERNAME                                    = "dockmind"
      SPRING_FLYWAY_DEFAULT_SCHEMA                   = schema
      SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA = schema
    }
  }

  environment = {
    "api-gateway" = merge(local.java_common, {
      SHIPMENT_SERVICE_URI = "http://shipment-service.${local.ns}:8081"
      DOCK_SERVICE_URI     = "http://dock-scheduling-service.${local.ns}:8082"
      RECOVERY_SERVICE_URI = "http://disruption-recovery-service.${local.ns}:8083"
      AI_SERVICE_URI       = "http://ai-operations-service.${local.ns}:8084"
      REDIS_HOST           = aws_elasticache_cluster.redis.cache_nodes[0].address
    })
    "shipment-service"        = merge(local.java_common, local.db_env["shipment-service"], { KAFKA_BOOTSTRAP_SERVERS = local.kafka })
    "dock-scheduling-service" = merge(local.java_common, local.db_env["dock-scheduling-service"], { KAFKA_BOOTSTRAP_SERVERS = local.kafka })
    "disruption-recovery-service" = merge(local.java_common, local.db_env["disruption-recovery-service"], {
      DOCK_SERVICE_URI = "http://dock-scheduling-service.${local.ns}:8082"
    })
    "ai-operations-service" = merge(local.java_common, {
      DOCK_SERVICE_URI     = "http://dock-scheduling-service.${local.ns}:8082"
      RECOVERY_SERVICE_URI = "http://disruption-recovery-service.${local.ns}:8083"
    })
    "keycloak" = {
      KC_HOSTNAME                     = local.public_url
      KC_HOSTNAME_BACKCHANNEL_DYNAMIC = "true"
      KC_PROXY_HEADERS                = "xforwarded"
      KC_BOOTSTRAP_ADMIN_USERNAME     = "admin"
      DOCKMIND_PUBLIC_URL             = local.public_url
    }
    "kafka" = {
      KAFKA_NODE_ID                                  = "1"
      KAFKA_PROCESS_ROLES                            = "broker,controller"
      KAFKA_LISTENERS                                = "PLAINTEXT://:9092,CONTROLLER://:9093"
      KAFKA_ADVERTISED_LISTENERS                     = "PLAINTEXT://${local.kafka}"
      KAFKA_CONTROLLER_LISTENER_NAMES                = "CONTROLLER"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP           = "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"
      KAFKA_CONTROLLER_QUORUM_VOTERS                 = "1@localhost:9093"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR         = "1"
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR = "1"
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR            = "1"
    }
    "frontend" = {}
  }

  secrets = {
    "api-gateway"                 = {}
    "shipment-service"            = { DB_PASSWORD = local.secret["db-password"] }
    "dock-scheduling-service"     = { DB_PASSWORD = local.secret["db-password"] }
    "disruption-recovery-service" = { DB_PASSWORD = local.secret["db-password"] }
    "ai-operations-service"       = { OPENAI_API_KEY = aws_secretsmanager_secret.openai.arn }
    "keycloak" = {
      KC_BOOTSTRAP_ADMIN_PASSWORD  = local.secret["keycloak-admin-password"]
      DEMO_USER_PASSWORD           = local.secret["demo-user-password"]
      DOCKMIND_SMOKE_CLIENT_SECRET = local.secret["smoke-client-secret"]
    }
    "kafka"    = {}
    "frontend" = {}
  }

  image = {
    for name in keys(local.apps) : name => (
      contains(local.built_images, name)
      ? "${aws_ecr_repository.app[name].repository_url}:${var.image_tag}"
      : "apache/kafka:4.1.0"
    )
  }
}

resource "aws_ecs_task_definition" "app" {
  for_each                 = local.apps
  family                   = "${local.name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64" # Graviton: cheaper per vCPU; images are built for arm64
  }

  container_definitions = jsonencode([{
    name         = each.key
    image        = local.image[each.key]
    essential    = true
    command      = each.key == "keycloak" ? ["start-dev", "--import-realm"] : null
    portMappings = [{ containerPort = each.value.port, protocol = "tcp" }]
    environment  = [for k, v in local.environment[each.key] : { name = k, value = v }]
    secrets      = [for k, v in local.secrets[each.key] : { name = k, valueFrom = v }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app[each.key].name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = each.key
      }
    }
  }])
}

resource "aws_ecs_service" "app" {
  for_each               = local.apps
  name                   = each.key
  cluster                = aws_ecs_cluster.main.id
  task_definition        = aws_ecs_task_definition.app[each.key].arn
  desired_count          = var.desired_count
  launch_type            = "FARGATE"
  enable_execute_command = false

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.tasks.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.app[each.key].arn
  }

  dynamic "load_balancer" {
    for_each = contains(keys(local.public_targets), each.key) ? [each.key] : []
    content {
      target_group_arn = aws_lb_target_group.app[load_balancer.value].arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [aws_lb_listener.http]
}
