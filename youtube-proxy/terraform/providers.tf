terraform {
  required_providers {
    koyeb = {
      source  = "koyeb/koyeb"
      version = "~> 0.1"
    }
  }
}

provider "koyeb" {
  # The Koyeb provider automatically uses the KOYEB_TOKEN environment variable.
}
