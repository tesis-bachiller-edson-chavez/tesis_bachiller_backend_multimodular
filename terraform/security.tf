# --- Security Group para la aplicación en Elastic Beanstalk ---
resource "aws_security_group" "app_sg" {
  name        = "tesis-app-sg"
  description = "Allows HTTP and HTTPS traffic to the application"
  vpc_id      = aws_vpc.main.id

  # La regla de egreso puede permanecer aquí, ya que no cambia.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- REGLAS DE ENTRADA DEFINIDAS COMO RECURSOS SEPARADOS ---

# Regla para permitir el tráfico entrante en el puerto 80 (HTTP)
resource "aws_security_group_rule" "app_ingress_http" {
  type              = "ingress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.app_sg.id
}

# Regla para permitir el tráfico entrante en el puerto 443 (HTTPS)
resource "aws_security_group_rule" "app_ingress_https" {
  type              = "ingress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.app_sg.id
}

# --- Security Group para la base de datos RDS ---
resource "aws_security_group" "db_sg" {
  name        = "tesis-db-sg"
  description = "Allows DB access only from the application"
  vpc_id      = aws_vpc.main.id # <-- AÑADIDO: Asocia este SG a nuestra VPC

  # Por defecto, no permite ningún tráfico entrante.
  # La regla de abajo lo define explícitamente.

  # Permite todo el tráfico saliente.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- Regla del Firewall: Conexión App -> DB ---
# Esta es la regla clave que conecta de forma segura la aplicación a la base de datos.
resource "aws_security_group_rule" "app_to_db" {
  type                     = "ingress"
  from_port                = 3306 # Puerto de MySQL
  to_port                  = 3306
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.app_sg.id
  security_group_id        = aws_security_group.db_sg.id
}
