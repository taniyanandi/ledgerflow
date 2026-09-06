resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_service_discovery_private_dns_namespace" "internal" {
  name = "${var.project_name}.local"
  vpc  = aws_vpc.main.id
}

resource "aws_service_discovery_service" "fraud_service" {
  name = "fraud-service"
  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.internal.id
    dns_records {
      ttl  = 10
      type = "A"
    }
  }
}

# --- security groups ---

resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb-sg"
  description = "Public ingress for the app's load balancer"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "app_service" {
  name        = "${var.project_name}-app-sg"
  description = "ledgerflow app: reachable only from the ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "fraud_service" {
  name        = "${var.project_name}-fraud-service-sg"
  description = "fraud-service: internal only, reachable only from the app"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 8000
    to_port         = 8000
    protocol        = "tcp"
    security_groups = [aws_security_group.app_service.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- log groups ---

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.project_name}-app"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "fraud_service" {
  name              = "/ecs/${var.project_name}-fraud-service"
  retention_in_days = 14
}

# --- fraud-service task + service (internal only, no public ALB) ---

resource "aws_ecs_task_definition" "fraud_service" {
  family                   = "${var.project_name}-fraud-service"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.fraud_service_task.arn

  container_definitions = jsonencode([{
    name  = "fraud-service"
    image = "${aws_ecr_repository.fraud_service.repository_url}:${var.fraud_service_image_tag}"
    portMappings = [{ containerPort = 8000, protocol = "tcp" }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.fraud_service.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "fraud-service"
      }
    }
  }])
}

resource "aws_ecs_service" "fraud_service" {
  name            = "${var.project_name}-fraud-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.fraud_service.arn
  desired_count   = var.fraud_service_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = aws_subnet.private[*].id
    security_groups = [aws_security_group.fraud_service.id]
  }

  service_registries {
    registry_arn = aws_service_discovery_service.fraud_service.arn
  }
}

# --- app task + service (public via ALB) ---

resource "aws_ecs_task_definition" "app" {
  family                   = "${var.project_name}-app"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.app_task.arn

  container_definitions = jsonencode([{
    name  = "app"
    image = "${aws_ecr_repository.app.repository_url}:${var.app_image_tag}"
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment = [
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${var.db_name}" },
      { name = "DB_USER", value = var.db_username },
      { name = "KAFKA_BOOTSTRAP", value = aws_msk_serverless_cluster.main.bootstrap_brokers_sasl_iam },
      { name = "EVENTS_TRANSPORT", value = "kafka" },
      { name = "FRAUD_BASE_URL", value = "http://fraud-service.${var.project_name}.local:8000" },
      { name = "AI_PROVIDER", value = "bedrock" },
      { name = "SPRING_AI_BEDROCK_ENABLED", value = "true" },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AI_MODEL_ID", value = var.bedrock_model_id },
    ]
    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.app.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "app"
      }
    }
  }])
}

resource "aws_ecs_service" "app" {
  name            = "${var.project_name}-app"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.app_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = aws_subnet.private[*].id
    security_groups = [aws_security_group.app_service.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.app]
}
