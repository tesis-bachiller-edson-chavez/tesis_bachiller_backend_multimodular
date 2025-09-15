# --- Red Virtual (VPC) ---
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "tesis-vpc"
  }
}

# --- Subredes Públicas ---
resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${data.aws_region.current.name}a"
  map_public_ip_on_launch = true # Importante para que las instancias tengan IP pública

  tags = {
    Name = "tesis-public-subnet-a"
  }
}

resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = "${data.aws_region.current.name}b"
  map_public_ip_on_launch = true

  tags = {
    Name = "tesis-public-subnet-b"
  }
}

# --- Subredes Privadas (para la Base de Datos) ---
resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.3.0/24"
  availability_zone = "${data.aws_region.current.name}a"

  tags = {
    Name = "tesis-private-subnet-a"
  }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.4.0/24"
  availability_zone = "${data.aws_region.current.name}b"

  tags = {
    Name = "tesis-private-subnet-b"
  }
}


# --- Gateway a Internet ---
resource "aws_internet_gateway" "gw" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "tesis-igw"
  }
}

# --- Tabla de Rutas para subredes públicas ---
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.gw.id
  }

  tags = {
    Name = "tesis-public-rt"
  }
}

# --- Asociaciones de la Tabla de Rutas ---
resource "aws_route_table_association" "public_a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

# --- Grupo de Subredes para RDS ---
# Le dice a RDS en qué subredes (privadas) debe colocar la base de datos
resource "aws_db_subnet_group" "rds_subnet_group" {
  name       = "tesis-rds-subnet-group"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id]

  tags = {
    Name = "Tesis RDS Subnet Group"
  }
}


# --- Obtener la Región Actual ---
data "aws_region" "current" {}
