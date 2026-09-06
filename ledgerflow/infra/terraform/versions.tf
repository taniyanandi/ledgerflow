terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state is left to the user to configure with their own bucket/table —
  # applying this module with only local state works fine for a first pass, but
  # is not safe for a team or repeated applies from different machines.
  #
  # backend "s3" {
  #   bucket         = "REPLACE-ME-terraform-state-bucket"
  #   key            = "ledgerflow/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "REPLACE-ME-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region
}
