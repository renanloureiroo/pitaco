import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";

import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";

import "./globals.css";

const inter = Inter({
  variable: "--font-sans",
  subsets: ["latin"],
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: "Painel Pitaco",
    template: "%s · Painel Pitaco",
  },
  description:
    "Operação do Pitaco: aplicações, chaves de acesso e pesquisas — da montagem à publicação.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-BR"
      className={cn(inter.variable, jetbrainsMono.variable, "h-full antialiased")}
    >
      <body className="min-h-full flex flex-col bg-background text-foreground">
        {children}
        <Toaster position="top-right" />
      </body>
    </html>
  );
}
