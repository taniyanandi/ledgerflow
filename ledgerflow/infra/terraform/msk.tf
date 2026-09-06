# MSK Serverless: auto-scaling storage/throughput, billed per use — fits this
# workload's low, spiky traffic far better than provisioning fixed brokers 24/7.
# Trade-off: client auth is IAM (SASL/IAM), so spring-kafka needs the
# `software.amazon.msk:aws-msk-iam-auth` client library and matching SASL config —
# a small extra wiring cost documented in this module's README.
#
# For guaranteed low-latency/high-throughput needs later, swap this for a
# provisioned `aws_msk_cluster` (broker type e.g. kafka.m5.large) — left as a
# reference below, commented out.

resource "aws_security_group" "msk" {
  name        = "${var.project_name}-msk-sg"
  description = "Allow Kafka (IAM auth) only from ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Kafka IAM-auth port from ECS tasks"
    from_port       = 9098
    to_port         = 9098
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

resource "aws_msk_serverless_cluster" "main" {
  cluster_name = "${var.project_name}-events"

  vpc_config {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.msk.id]
  }

  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }
}

# --- Provisioned MSK alternative (higher ops, no IAM-auth client library needed) ---
# resource "aws_msk_cluster" "main" {
#   cluster_name           = "${var.project_name}-events"
#   kafka_version          = "3.8.0"
#   number_of_broker_nodes = 2
#   broker_node_group_info {
#     instance_type   = "kafka.m5.large"
#     client_subnets  = aws_subnet.private[*].id
#     security_groups = [aws_security_group.msk.id]
#     storage_info {
#       ebs_storage_info { volume_size = 100 }
#     }
#   }
# }
