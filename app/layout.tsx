import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "OTP Hub — Personal security workspace",
  description: "A privacy-first workspace for managing your own authentication methods.",
  applicationName: "OTP Hub"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
