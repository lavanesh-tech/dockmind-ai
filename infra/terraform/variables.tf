variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name, used in resource names and tags."
  type        = string
  default     = "dev"
}

variable "image_tag" {
  description = "Container image tag to deploy (the Git commit pushed to ECR by the pipeline)."
  type        = string
}

variable "desired_count" {
  description = "Tasks per service. Set to 0 to stop all containers and most of the cost while keeping the data."
  type        = number
  default     = 1
}

variable "db_instance_class" {
  description = "RDS instance size. db.t4g.micro is the smallest PostgreSQL instance."
  type        = string
  default     = "db.t4g.micro"
}

variable "deletion_protection" {
  description = "Protect the database from accidental deletion. Turn off only to tear the environment down."
  type        = bool
  default     = false
}
