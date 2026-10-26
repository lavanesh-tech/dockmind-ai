# One public load balancer. Paths decide where a request goes:
#   /api/*                     -> API gateway (which applies auth, rate limits, circuit breakers)
#   /realms/*, /resources/*    -> Keycloak login pages and tokens (the admin console is NOT exposed)
#   everything else            -> the web app
resource "aws_lb" "app" {
  name                       = local.name
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = aws_subnet.public[*].id
  drop_invalid_header_fields = true
}

locals {
  public_targets = {
    "api-gateway" = local.apps["api-gateway"]
    "keycloak"    = local.apps["keycloak"]
    "frontend"    = local.apps["frontend"]
  }
}

resource "aws_lb_target_group" "app" {
  for_each    = local.public_targets
  name        = substr("${local.name}-${each.key}", 0, 32)
  port        = each.value.port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    path                = each.value.health
    matcher             = "200"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 5
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app["frontend"].arn
  }
}

resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app["api-gateway"].arn
  }
  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}

resource "aws_lb_listener_rule" "keycloak" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 20
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app["keycloak"].arn
  }
  condition {
    path_pattern {
      values = ["/realms/*", "/resources/*"]
    }
  }
}
