data "aws_iam_policy_document" "assume_ec2" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name_prefix        = "${var.project}-"
  assume_role_policy = data.aws_iam_policy_document.assume_ec2.json
}

# Session Manager: shell and port forwarding without SSH keys or open ports
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "bucket" {
  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.this.arn]
  }
  statement {
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.this.arn}/*"]
  }
}

resource "aws_iam_role_policy" "bucket" {
  name   = "bench-bucket"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.bucket.json
}

resource "aws_iam_instance_profile" "instance" {
  name_prefix = "${var.project}-"
  role        = aws_iam_role.instance.name
}
