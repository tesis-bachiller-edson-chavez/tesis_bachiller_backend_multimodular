resource "aws_ecr_repository" "app_repo" {
  name = "tesis-backend" # El nombre que tendrá tu repositorio en Amazon ECR

  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}
