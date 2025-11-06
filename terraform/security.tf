# --- Security Group para la aplicación en Elastic Beanstalk ---
# Este es el "portero" de las instancias EC2.
resource "aws_security_group" "app_sg" {
  name        = "tesis-app-sg"
  description = "Allows inbound traffic from the Load Balancer"
  vpc_id      = aws_vpc.main.id

  # Permite el tráfico en el puerto 8080 desde cualquier lugar dentro de la VPC
  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [aws_vpc.main.cidr_block]
  }

  # Permite todo el tráfico saliente.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- Security Group para la base de datos RDS ---
resource "aws_security_group" "db_sg" {
  name        = "tesis-db-sg"
  description = "Allows DB access only from the application"
  vpc_id      = aws_vpc.main.id

  # Permite todo el tráfico saliente.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- Regla del Firewall: Conexión App -> DB ---
resource "aws_security_group_rule" "app_to_db" {
  type                     = "ingress"
  from_port                = 3306 # Puerto de MySQL
  to_port                  = 3306
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.app_sg.id
  security_group_id        = aws_security_group.db_sg.id
}
