output "results_bucket" {
  description = "S3 bucket holding results/<instance>/ for every machine."
  value       = aws_s3_bucket.this.id
}

output "region" {
  value = var.region
}

output "machines" {
  description = "What was launched where."
  value = {
    for name, inst in aws_instance.bench : name => {
      id            = inst.id
      instance_type = inst.instance_type
      az            = inst.availability_zone
      arch          = local.machines[name].arch
      gpu           = local.machines[name].gpu
    }
  }
}

output "ssm_shell" {
  description = "Open a shell on a machine (needs the Session Manager plugin for the AWS CLI)."
  value = {
    for name, inst in aws_instance.bench : name =>
    "aws ssm start-session --region ${var.region} --target ${inst.id}"
  }
}

output "ssm_port_forward" {
  description = "Forward the machine's llama-server (127.0.0.1:8080) to localhost:8080."
  value = {
    for name, inst in aws_instance.bench : name =>
    "aws ssm start-session --region ${var.region} --target ${inst.id} --document-name AWS-StartPortForwardingSession --parameters portNumber=8080,localPortNumber=8080"
  }
}
