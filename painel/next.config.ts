import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Empacota o servidor e só o node_modules que ele usa em .next/standalone,
  // que é o que a imagem Docker copia.
  output: "standalone",
  transpilePackages: ["@pitaco/react-native"],
  turbopack: {
    resolveAlias: {
      "react-native": "react-native-web",
    },
    resolveExtensions: [
      ".web.js",
      ".web.jsx",
      ".web.ts",
      ".web.tsx",
      ".mjs",
      ".js",
      ".jsx",
      ".json",
      ".ts",
      ".tsx",
    ],
  },
  webpack: (config) => {
    config.resolve.alias = {
      ...(config.resolve.alias || {}),
      "react-native$": "react-native-web",
    };
    config.resolve.extensions = [
      ".web.js",
      ".web.jsx",
      ".web.ts",
      ".web.tsx",
      ...config.resolve.extensions,
    ];
    return config;
  },
};

export default nextConfig;
