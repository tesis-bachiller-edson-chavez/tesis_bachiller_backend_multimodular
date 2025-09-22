# --- Security Group para la aplicación en Elastic Beanstalk ---
# Este es el "portero" de las instancias EC2.
resource "aws_security_group" "app_sg" {
  name        = "tesis-app-sg"
  description = "Allows inbound traffic from the Load Balancer"
  vpc_id      = aws_vpc.main.id

  # Permite todo el tráfico saliente.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- Regla de Entrada para la Aplicación ---
# Permite el tráfico en el puerto 80 (donde escucha la app) SOLO si viene
# desde el Security Group del Load Balancer.
resource "aws_security_group_rule" "allow_traffic_from_alb" {
  type                     = "ingress"
  from_port                = 80
  to_port                  = 80
  protocol                 = "tcp"
  # Referencia al SG del ALB que Beanstalk crea automáticamente.
  # Nota: Beanstalk debe gestionar el SG del ALB para que esto funcione.
  source_security_group_id = aws_elastic_beanstalk_environment.tesis_env.load_balancers[0] != "" ? tolist(data.aws_lb.tesis_alb.security_groups)[0] : null
  security_group_id        = aws_security_group.app_sg.id
}

# --- Data source para obtener información del ALB creado por Beanstalk ---
data "aws_lb" "tesis_alb" {
  # El ARN del ALB es una de las salidas del recurso del entorno.
  arn = aws_elastic_beanstalk_environment.tesis_env.load_balancers[0]
  depends_on = [aws_elastic_beanstalk_environment.tesis_env]
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
