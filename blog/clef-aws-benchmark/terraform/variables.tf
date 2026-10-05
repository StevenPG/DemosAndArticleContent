variable "region" {
  description = "AWS region. us-east-1 has every instance type below in several AZs."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Name prefix for every resource."
  type        = string
  default     = "clef-bench"
}

variable "instances" {
  description = <<-EOT
    Machines to benchmark: name => EC2 instance type. Architecture (x86_64/arm64) and GPU presence
    are read from the EC2 API, so any type works. 32 GB of RAM (CPU) or 24 GB of VRAM (GPU) fits
    every Clef-flash quantization including BF16.
  EOT
  type        = map(string)
  default = {
    "c7i-4xlarge" = "c7i.4xlarge" # Intel Sapphire Rapids, 16 vCPU, 32 GB, AVX-512 + AMX
    "c7a-4xlarge" = "c7a.4xlarge" # AMD EPYC Genoa,       16 vCPU, 32 GB, AVX-512
    "c8g-4xlarge" = "c8g.4xlarge" # AWS Graviton4,        16 vCPU, 32 GB, SVE2
    "g6-xlarge"   = "g6.xlarge"   # NVIDIA L4 24 GB,       4 vCPU, 16 GB
    "g6e-xlarge"  = "g6e.xlarge"  # NVIDIA L40S 48 GB,     4 vCPU, 32 GB
  }
}

variable "only" {
  description = "Launch only these keys of var.instances (empty = all). e.g. [\"c8g-4xlarge\", \"g6-xlarge\"]"
  type        = list(string)
  default     = []
}

variable "quants" {
  description = "Clef-Flash GGUF quantizations to benchmark on every machine, from ggml-org/Clef-Flash-GGUF."
  type        = list(string)
  default     = ["Q4_K_M", "Q8_0", "BF16"]
}

variable "serve_quant" {
  description = "Quantization left running on 127.0.0.1:8080 after the benchmark, for interactive calls."
  type        = string
  default     = "Q4_K_M"
}

variable "llama_cpp_tag" {
  description = "llama.cpp release tag to build. Clef support needs b11390 or newer."
  type        = string
  default     = "b11401"
}

variable "auto_run" {
  description = "Run the benchmark on first boot. false = install everything, then wait for you."
  type        = bool
  default     = true
}

variable "bench_args" {
  description = "Extra arguments for bench/clef_bench.py on every machine."
  type        = string
  default     = "--concurrency 1,4 --duration 45 --min-requests 6 --max-step-seconds 420"
}

variable "shutdown_minutes" {
  description = "Safety net: each instance powers itself off this many minutes after boot (0 = never). Stopped instances only bill for EBS."
  type        = number
  default     = 300
}

variable "root_volume_gb" {
  description = "Root volume size. The three default quants are 34 GB; the Deep Learning AMI itself needs ~75 GB."
  type        = number
  default     = 150
}

variable "use_spot" {
  description = "Request Spot capacity (roughly 60-70% cheaper, can be interrupted)."
  type        = bool
  default     = false
}
