resource "aws_db_instance" "tesis_db" {
  # --- Configuración de la Capa Gratuita ---
  engine               = "mysql"
  engine_version       = "8.0.43"
  instance_class       = "db.t3.micro"
  allocated_storage    = 20
  storage_type         = "gp2"

  # --- Identificadores y Credenciales ---
  identifier           = "tesis-database"
  db_name              = "tesisdb"
  username             = "admin"
  password             = var.db_password

  # --- Conectividad y Seguridad ---
  db_subnet_group_name = aws_db_subnet_group.rds_subnet_group.name # <-- AÑADIDO: Coloca la BD en subredes privadas
  vpc_security_group_ids = [aws_security_group.db_sg.id]
  skip_final_snapshot  = true
}
