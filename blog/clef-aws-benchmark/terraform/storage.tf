# One bucket carries the benchmark code to the instances (project/) and the results back (results/).
# Uploading the local working copy means your edits run without pushing them anywhere first.

resource "aws_s3_bucket" "this" {
  bucket_prefix = "${var.project}-"
  force_destroy = true # terraform destroy also removes results; fetch them first
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket                  = aws_s3_bucket.this.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

locals {
  project_root = "${path.module}/.."
  project_files = [
    for f in setunion(
      fileset(local.project_root, "scripts/remote/*"),
      fileset(local.project_root, "bench/**"),
      ["scripts/bench-workers-ai.sh"],
    ) : f
    if !strcontains(f, "__pycache__")
  ]
}

resource "aws_s3_object" "project" {
  for_each = toset(local.project_files)

  bucket = aws_s3_bucket.this.id
  key    = "project/${each.value}"
  source = "${local.project_root}/${each.value}"
  etag   = filemd5("${local.project_root}/${each.value}")
}
