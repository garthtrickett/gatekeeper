resource "koyeb_app" "gatekeeper" {
  name = var.app_name
}

resource "koyeb_service" "youtube_proxy" {
  app_name = koyeb_app.gatekeeper.name

  definition {
    name = var.service_name
    
    instance_types {
      type = "free"
    }

    regions = ["fra"]

    ports {
      port     = 8000
      protocol = "http"
    }

    routes {
      path = "/"
      port = 8000
    }

    env {
      key   = "PORT"
      value = "8000"
    }

    git {
      repository = var.git_repository
      branch     = var.git_branch
      workdir    = "youtube-proxy"

      dockerfile {
        dockerfile = "Dockerfile"
      }
    }
  }
}
