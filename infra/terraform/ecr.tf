# One image repository per built image. Tags are immutable (one tag = one exact build),
# images are scanned for known vulnerabilities on push, and only the last 10 are kept.
resource "aws_ecr_repository" "app" {
  for_each             = local.built_images
  name                 = "dockmind/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 10 most recent images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}
