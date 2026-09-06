variable "aws_region" {
  description = "AWS region to deploy into. Must be a region where the chosen Bedrock model is available."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Short name used to prefix all resources."
  type        = string
  default     = "ledgerflow"
}

variable "environment" {
  description = "Deployment environment tag (e.g. dev, staging, prod)."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "Application database name."
  type        = string
  default     = "ledgerflow"
}

variable "db_username" {
  description = "Master username for RDS. The password is generated and stored in Secrets Manager."
  type        = string
  default     = "ledgerflow"
}

variable "app_image_tag" {
  description = "Image tag to deploy for the ledgerflow app service."
  type        = string
  default     = "latest"
}

variable "fraud_service_image_tag" {
  description = "Image tag to deploy for the fraud-service."
  type        = string
  default     = "latest"
}

variable "app_desired_count" {
  description = "Number of app service tasks."
  type        = number
  default     = 1
}

variable "fraud_service_desired_count" {
  description = "Number of fraud-service tasks."
  type        = number
  default     = 1
}

variable "bedrock_model_id" {
  description = "Bedrock model id the app's task role is scoped to invoke (e.g. anthropic.claude-3-5-haiku-20241022-v1:0)."
  type        = string
  default     = "anthropic.claude-3-5-haiku-20241022-v1:0"
}
