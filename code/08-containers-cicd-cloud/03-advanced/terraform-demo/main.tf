terraform {
  required_providers {
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

variable "environment" {
  description = "which environment this config file is for"
  type        = string
  default     = "staging"
}

variable "replica_count" {
  description = "simulates a real infra parameter, e.g. desired replica count"
  type        = number
  default     = 2
}

# Stands in for "a real resource" (an RDS instance, an S3 bucket) without needing cloud
# credentials - the point here is the init/plan/apply/state/drift WORKFLOW, which is identical
# regardless of provider.
resource "local_file" "app_config" {
  filename = "${path.module}/generated/app-config.json"
  content = jsonencode({
    environment   = var.environment
    replica_count = var.replica_count
  })
}

output "config_path" {
  value = local_file.app_config.filename
}
