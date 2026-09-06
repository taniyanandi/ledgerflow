data "aws_caller_identity" "current" {}

# --- ECS task execution role (shared): pull images, write logs, read the DB secret ---
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "${var.project_name}-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "read_db_secret" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db_password.arn]
  }
}

resource "aws_iam_role_policy" "task_execution_secrets" {
  name   = "${var.project_name}-read-db-secret"
  role   = aws_iam_role.task_execution.id
  policy = data.aws_iam_policy_document.read_db_secret.json
}

# --- App task role: Bedrock (scoped to the chosen model) + MSK IAM auth ---
data "aws_iam_policy_document" "app_task" {
  statement {
    sid       = "InvokeBedrockModel"
    actions   = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
    resources = ["arn:aws:bedrock:${var.aws_region}::foundation-model/${var.bedrock_model_id}"]
  }

  statement {
    sid = "MskIamAuth"
    actions = [
      "kafka-cluster:Connect",
      "kafka-cluster:AlterCluster",
      "kafka-cluster:DescribeCluster",
    ]
    resources = [aws_msk_serverless_cluster.main.arn]
  }

  statement {
    sid = "MskTopicAccess"
    actions = [
      "kafka-cluster:*Topic*",
      "kafka-cluster:WriteData",
      "kafka-cluster:ReadData",
    ]
    resources = ["${aws_msk_serverless_cluster.main.arn}/*"]
  }

  statement {
    sid       = "MskConsumerGroup"
    actions   = ["kafka-cluster:AlterGroup", "kafka-cluster:DescribeGroup"]
    resources = ["${aws_msk_serverless_cluster.main.arn}/*"]
  }
}

resource "aws_iam_role" "app_task" {
  name               = "${var.project_name}-app-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy" "app_task" {
  name   = "${var.project_name}-app-task-policy"
  role   = aws_iam_role.app_task.id
  policy = data.aws_iam_policy_document.app_task.json
}

# --- fraud-service task role: minimal, no Bedrock/MSK access needed ---
resource "aws_iam_role" "fraud_service_task" {
  name               = "${var.project_name}-fraud-service-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}
