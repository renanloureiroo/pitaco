import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Empacota o servidor e só o node_modules que ele usa em .next/standalone,
  // que é o que a imagem Docker copia.
  output: "standalone",
};

export default nextConfig;
