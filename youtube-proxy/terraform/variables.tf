variable "app_name" {
  type        = string
  description = "The name of the Koyeb App"
  default     = "gatekeeper-app"
}

variable "service_name" {
  type        = string
  description = "The name of the Koyeb Service"
  default     = "youtube-proxy"
}

variable "git_repository" {
  type        = string
  description = "The GitHub repository containing the Gatekeeper project (e.g., github.com/username/gatekeeper)"
}

variable "git_branch" {
  type        = string
  description = "The branch to deploy"
  default     = "main"
}
