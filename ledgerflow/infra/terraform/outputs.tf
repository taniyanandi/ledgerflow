output "alb_dns_name" {
  description = "Public URL for the ledgerflow app (add http:// in front)."
  value       = aws_lb.app.dns_name
}

output "ecr_app_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "ecr_fraud_service_repository_url" {
  value = aws_ecr_repository.fraud_service.repository_url
}

output "rds_endpoint" {
  value = aws_db_instance.main.endpoint
}

output "msk_bootstrap_brokers_sasl_iam" {
  value = aws_msk_serverless_cluster.main.bootstrap_brokers_sasl_iam
}

output "bedrock_model_arn" {
  value = "arn:aws:bedrock:${var.aws_region}::foundation-model/${var.bedrock_model_id}"
}
