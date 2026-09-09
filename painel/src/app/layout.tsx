import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";

import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";

import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
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
      className={cn(geistSans.variable, geistMono.variable, "h-full antialiased")}
    >
      <body className="min-h-full flex flex-col bg-background text-foreground">
        {children}
        <Toaster position="top-right" />
      </body>
    </html>
  );
}
