terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws",
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  # Cambiando la región a Ohio
  region = "us-east-2"

  # --- Etiquetas por Defecto para todos los recursos ---
  # Estas etiquetas se aplicarán automáticamente a todos los recursos que las soporten.
  default_tags {
    tags = {
      Project     = "tesis-bachiller"
      Owner       = "grubhart" # Puedes poner tu nombre o identificador aquí
      ManagedBy   = "Terraform"
    }
  }
}
