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

variable "github_client_id" {
  description = "client_id de la aplicación de GitHub"
  type        = string
  sensitive   = true
}

variable "github_client_secret" {
  description = "client_secret de la aplicación de GitHub"
  type        = string
  sensitive   = true
}

variable "oauth2_github_redirect_uri" {
  description = "La URL de callback completa para el flujo de OAuth2 de GitHub."
  type        = string
}

variable "ssl_certificate_arn" {
  description = "El ARN del certificado SSL de ACM para el dominio."
  type        = string
}

variable "dora_initial_admin_username" {
  description = "The GitHub username of the user who will become the first administrator."
  type        = string
  sensitive   = true
}

variable "dora_github_organization_name" {
  description = "The name of the GitHub organization whose members are allowed to use the application."
  type        = string
}

variable "environment_name" {
  description = "El nombre del entorno de despliegue (e.g., 'production', 'staging')."
  type        = string
  default     = "development"
}

variable "datadog_api_key" {
  description = "La clave de API de Datadog para la instalación del agente."
  type        = string
  sensitive   = true
}

variable "datadog_application_key" {
  description = "La clave de aplicación de Datadog para acceder a la API de Incidents."
  type        = string
  sensitive   = true
}
