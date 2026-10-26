terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # State is local by default. For a team, use a versioned, encrypted S3 bucket with locking:
  # backend "s3" {
  #   bucket       = "dockmind-terraform-state-<account-id>"
  #   key          = "dev/terraform.tfstate"
  #   region       = "us-east-1"
  #   encrypt      = true
  #   use_lockfile = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "dockmind"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
