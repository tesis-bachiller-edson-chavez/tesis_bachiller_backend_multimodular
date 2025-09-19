variable "db_password" {
  description = "La contraseña para la base de datos RDS."
  type        = string
  sensitive   = true
}

variable "github_api_token" {
  description = "Personal Access Token de GitHub para que la aplicación acceda a la API."
  type        = string
  sensitive   = true
}

variable "app_cors_allowed_origins" {
  description = "Lista de URLs de frontend permitidas para CORS, separadas por comas."
  type        = string
  default     = "http://localhost:3000,https://d14lcc05xzsyip.cloudfront.net"
}

variable "app_frontend_url" {
  description = "La URL base del frontend para construir redirecciones de OAuth2."
  type        = string
  default     = "https://d14lcc05xzsyip.cloudfront.net"
}
