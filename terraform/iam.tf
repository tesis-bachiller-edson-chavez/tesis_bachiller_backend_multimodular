# --- Rol de IAM para el servicio de Elastic Beanstalk ---
resource "aws_iam_role" "beanstalk_service_role" {
  name = "beanstalk-service-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Effect = "Allow",
        Principal = {
          Service = "elasticbeanstalk.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "beanstalk_service_policy" {
  role       = aws_iam_role.beanstalk_service_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSElasticBeanstalkService"
}

# --- Rol de IAM y Perfil de Instancia para las instancias EC2 de Beanstalk ---
resource "aws_iam_role" "beanstalk_ec2_role" {
  name = "beanstalk-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Effect = "Allow",
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_instance_profile" "beanstalk_ec2_profile" {
  name = "beanstalk-ec2-profile"
  role = aws_iam_role.beanstalk_ec2_role.name
}

# Política para que la instancia funcione en un entorno de Beanstalk
resource "aws_iam_role_policy_attachment" "beanstalk_ec2_webtier" {
  role       = aws_iam_role.beanstalk_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AWSElasticBeanstalkWebTier"
}

# Política para que la instancia pueda descargar imágenes de ECR (¡muy importante!)
resource "aws_iam_role_policy_attachment" "beanstalk_ec2_ecr" {
  role       = aws_iam_role.beanstalk_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}
