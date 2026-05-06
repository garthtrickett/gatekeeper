{
  description = "Gatekeeper Surgical Proxy - yt-dlp Tunnel";

  inputs = {
    # Use unstable to get the most recent yt-dlp updates
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };

        # Define our Python environment with our exact dependencies
        pythonEnv = pkgs.python3.withPackages (ps: with ps; [
          fastapi
          uvicorn
          requests
          yt-dlp
        ]);
      in
      {
        # Development Shell (Replaces venv)
        devShells.default = pkgs.mkShell {
          buildInputs = [ pythonEnv ];

          shellHook = ''
            echo "🛡️ Gatekeeper Surgical Proxy Environment Loaded!"
            echo "▶️  Run the server with: uvicorn surgical_proxy:app --host 0.0.0.0 --port 8000 --reload"
          '';
        };

        # Optional: Define a package so you can run it directly via `nix run`
        packages.default = pkgs.writeShellScriptBin "start-proxy" ''
          ${pythonEnv}/bin/uvicorn surgical_proxy:app --host 0.0.0.0 --port 8000
        '';
        apps.default = {
          type = "app";
          program = "${self.packages.${system}.default}/bin/start-proxy";
        };
      }
    );
}
