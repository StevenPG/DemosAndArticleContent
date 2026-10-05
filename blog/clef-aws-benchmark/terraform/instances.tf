locals {
  selected = {
    for name, type in var.instances : name => type
    if length(var.only) == 0 || contains(var.only, name)
  }
}

# Architecture and GPU count straight from EC2, so adding an instance type is a one-line change.
data "aws_ec2_instance_type" "this" {
  for_each      = toset(values(local.selected))
  instance_type = each.value
}

# Not every type is offered in every AZ (g6e in particular). Pick the first AZ that has it.
data "aws_ec2_instance_type_offerings" "this" {
  for_each      = toset(values(local.selected))
  location_type = "availability-zone"

  filter {
    name   = "instance-type"
    values = [each.value]
  }
}

# Ubuntu 24.04 for CPU boxes; the Deep Learning Base AMI (Ubuntu 24.04, NVIDIA driver + CUDA
# toolkit preinstalled) for GPU boxes. Both resolved through AWS-maintained public SSM parameters.
data "aws_ssm_parameter" "ami" {
  for_each = {
    cpu_x86_64 = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
    cpu_arm64  = "/aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id"
    gpu_x86_64 = "/aws/service/deeplearning/ami/x86_64/base-oss-nvidia-driver-gpu-ubuntu-24.04/latest/ami-id"
  }
  name = each.value
}

locals {
  machines = {
    for name, type in local.selected : name => {
      type = type
      arch = contains(data.aws_ec2_instance_type.this[type].supported_architectures, "arm64") ? "arm64" : "x86_64"
      gpu  = length(data.aws_ec2_instance_type.this[type].gpus) > 0
      az = [
        for az in sort(tolist(data.aws_ec2_instance_type_offerings.this[type].locations)) : az
        if contains(keys(aws_subnet.public), az)
      ][0]
    }
  }
}

resource "aws_instance" "bench" {
  for_each = local.machines

  instance_type          = each.value.type
  ami                    = data.aws_ssm_parameter.ami["${each.value.gpu ? "gpu" : "cpu"}_${each.value.arch}"].value
  subnet_id              = aws_subnet.public[each.value.az].id
  vpc_security_group_ids = [aws_security_group.egress_only.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  # "stop" keeps the box (and its downloaded models) around after the safety-net shutdown;
  # one-time Spot requests can only terminate.
  instance_initiated_shutdown_behavior = var.use_spot ? "terminate" : "stop"

  dynamic "instance_market_options" {
    for_each = var.use_spot ? [1] : []
    content {
      market_type = "spot"
    }
  }

  metadata_options {
    http_tokens = "required" # IMDSv2 only
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    iops        = 6000
    throughput  = 500 # MB/s; model load time is part of what we measure
    encrypted   = true
  }

  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    bucket           = aws_s3_bucket.this.id
    region           = var.region
    name             = each.key
    instance_type    = each.value.type
    quants           = join(" ", var.quants)
    serve_quant      = var.serve_quant
    llama_cpp_tag    = var.llama_cpp_tag
    auto_run         = var.auto_run
    bench_args       = var.bench_args
    shutdown_minutes = var.shutdown_minutes
  })

  tags = { Name = "${var.project}-${each.key}" }

  depends_on = [aws_s3_object.project, aws_iam_role_policy.bucket, aws_route_table_association.public]
}
