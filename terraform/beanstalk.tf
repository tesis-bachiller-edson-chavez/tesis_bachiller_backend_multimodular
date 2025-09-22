resource "aws_elastic_beanstalk_application" "tesis_app" {
  name        = "tesis-backend-app"
  description = "Backend application for the bachelor thesis"
}

resource "aws_elastic_beanstalk_environment" "tesis_env" {
  name                = "tesis-backend-env"
  application         = aws_elastic_beanstalk_application.tesis_app.name
  solution_stack_name = "64bit Amazon Linux 2023 v4.7.0 running Docker"

  # --- Configuración Mínima para Forzar ALB en VPC ---
  # El resto de la configuración (HTTPS, HealthCheck) se hará vía .ebextensions.
  setting {
    namespace = "aws:elasticbeanstalk:environment"
    name      = "LoadBalancerType"
    value     = "application"
  }
  setting {
    namespace = "aws:ec2:vpc"
    name      = "VPCId"
    value     = aws_vpc.main.id
  }
  setting {
    namespace = "aws:ec2:vpc"
    name      = "Subnets"
    value     = join(",", [aws_subnet.public_a.id, aws_subnet.public_b.id])
  }
  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "SecurityGroups"
    value     = aws_security_group.app_sg.id
  }

  # --- Configuración de Instancia y Roles ---
  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "InstanceType"
    value     = "t2.micro"
  }
  setting {
    namespace = "aws:autoscaling:asg"
    name      = "MinSize"
    value     = "1"
  }
  setting {
    namespace = "aws:autoscaling:asg"
    name      = "MaxSize"
    value     = "2"
  }
  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "IamInstanceProfile"
    value     = aws_iam_instance_profile.beanstalk_ec2_profile.name
  }
  setting {
    namespace = "aws:elasticbeanstalk:environment"
    name      = "ServiceRole"
    value     = aws_iam_role.beanstalk_service_role.name
  }

  # --- Variables de Entorno ---
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "JDBC_DATABASE_URL"
    value     = "jdbc:mysql://${aws_db_instance.tesis_db.address}:${aws_db_instance.tesis_db.port}/${aws_db_instance.tesis_db.db_name}"
  }
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "JDBC_DATABASE_USERNAME"
    value     = aws_db_instance.tesis_db.username
  }
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "JDBC_DATABASE_PASSWORD"
    value     = var.db_password
  }
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID"
    value     = var.github_client_id
  }
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET"
    value     = var.github_client_secret
  }
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "DORA_GITHUB_API_TOKEN"
    value     = var.github_api_token
  }
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "APP_CORS_ALLOWED_ORIGINS"
    value     = var.app_cors_allowed_origins
  }
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "APP_FRONTEND_URL"
    value     = var.app_frontend_url
  }
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "OAUTH2_GITHUB_REDIRECT_URI"
    value     = var.oauth2_github_redirect_uri
  }
}
