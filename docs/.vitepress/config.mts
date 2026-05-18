import { defineConfig } from "vitepress";

// Cloudflare Workers serves from /; GitHub Pages serves under
// /expo-callkit-telecom/ and sets DOCS_BASE accordingly in CI.
const base = process.env.DOCS_BASE ?? "/";

export default defineConfig({
  title: "expo-callkit-telecom",
  description:
    "CallKit and Core-Telecom for React Native and Expo — VoIP push, incoming call UI, LiveKit-friendly audio. A modern react-native-callkeep alternative.",

  base,

  cleanUrls: true,
  lastUpdated: true,

  sitemap: {
    hostname: "https://expo-callkit-telecom.mfairley.com/",
  },

  head: [
    ["link", { rel: "icon", type: "image/png", href: `${base}favicon.png` }],
    ["link", { rel: "apple-touch-icon", href: `${base}apple-touch-icon.png` }],
    ["meta", { name: "theme-color", content: "#3c82f6" }],
    [
      "script",
      {
        defer: "",
        src: "https://static.cloudflareinsights.com/beacon.min.js",
        "data-cf-beacon": '{"token": "6cc3f5012232464fa4876d020cadc147"}',
      },
    ],
    [
      "meta",
      {
        property: "og:type",
        content: "website",
      },
    ],
    [
      "meta",
      {
        property: "og:site_name",
        content: "expo-callkit-telecom",
      },
    ],
    [
      "meta",
      {
        property: "og:title",
        content: "expo-callkit-telecom — CallKit & Core-Telecom for React Native + Expo",
      },
    ],
    [
      "meta",
      {
        property: "og:description",
        content:
          "VoIP push, incoming call UI, LiveKit-friendly audio. A modern react-native-callkeep alternative.",
      },
    ],
    [
      "meta",
      {
        property: "og:url",
        content: "https://expo-callkit-telecom.mfairley.com/",
      },
    ],
    [
      "meta",
      {
        property: "og:image",
        content: "https://expo-callkit-telecom.mfairley.com/og-image.png",
      },
    ],
    ["meta", { property: "og:image:width", content: "1200" }],
    ["meta", { property: "og:image:height", content: "630" }],
    [
      "meta",
      {
        property: "og:image:alt",
        content:
          "expo-callkit-telecom — CallKit & Core-Telecom for React Native and Expo (VoIP push · LiveKit-friendly audio)",
      },
    ],
    [
      "meta",
      {
        name: "twitter:card",
        content: "summary_large_image",
      },
    ],
    [
      "meta",
      {
        name: "twitter:title",
        content: "expo-callkit-telecom — CallKit & Core-Telecom for React Native + Expo",
      },
    ],
    [
      "meta",
      {
        name: "twitter:description",
        content:
          "VoIP push, incoming call UI, LiveKit-friendly audio. A modern react-native-callkeep alternative.",
      },
    ],
    [
      "meta",
      {
        name: "twitter:image",
        content: "https://expo-callkit-telecom.mfairley.com/og-image.png",
      },
    ],
    [
      "meta",
      {
        name: "twitter:image:alt",
        content:
          "expo-callkit-telecom — CallKit & Core-Telecom for React Native and Expo (VoIP push · LiveKit-friendly audio)",
      },
    ],
    [
      "script",
      { type: "application/ld+json" },
      JSON.stringify({
        "@context": "https://schema.org",
        "@type": "SoftwareSourceCode",
        name: "expo-callkit-telecom",
        description:
          "CallKit and Core-Telecom for React Native and Expo — VoIP push, incoming call UI, LiveKit-friendly audio. A modern react-native-callkeep alternative.",
        codeRepository: "https://github.com/mfairley/expo-callkit-telecom",
        url: "https://expo-callkit-telecom.mfairley.com/",
        license: "https://opensource.org/licenses/MIT",
        programmingLanguage: ["TypeScript", "Swift", "Kotlin"],
        runtimePlatform: ["iOS", "Android", "React Native", "Expo"],
        author: {
          "@type": "Person",
          name: "Michael Fairley",
          url: "https://github.com/mfairley",
        },
      }),
    ],
    [
      "script",
      {
        defer: "",
        src: "https://static.cloudflareinsights.com/beacon.min.js",
        "data-cf-beacon": '{"token": "d527696ea03b4d9db1f8a65b45b6734f"}',
      },
    ],
  ],

  themeConfig: {
    logo: {
      light: "/app-icon-squircle-light.png",
      dark: "/app-icon-squircle-dark.png",
      alt: "expo-callkit-telecom",
    },

    nav: [
      { text: "Guide", link: "/getting-started" },
      { text: "API", link: "/api/" },
      { text: "vs callkeep", link: "/vs-callkeep" },
      { text: "npm", link: "https://www.npmjs.com/package/expo-callkit-telecom" },
    ],

    sidebar: [
      {
        text: "Introduction",
        items: [
          { text: "Overview", link: "/" },
          { text: "Getting started", link: "/getting-started" },
        ],
      },
      {
        text: "Reference",
        items: [
          { text: "VoIP push payload", link: "/voip-push" },
          { text: "Platform notes", link: "/platform-notes" },
          { text: "Verified against", link: "/verified-against" },
          { text: "API reference", link: "/api/" },
        ],
      },
      {
        text: "Comparisons",
        items: [{ text: "vs react-native-callkeep", link: "/vs-callkeep" }],
      },
    ],

    socialLinks: [
      { icon: "github", link: "https://github.com/mfairley/expo-callkit-telecom" },
    ],

    editLink: {
      pattern:
        "https://github.com/mfairley/expo-callkit-telecom/edit/main/docs/:path",
      text: "Edit this page on GitHub",
    },

    search: {
      provider: "local",
    },

    footer: {
      message: "Released under the MIT License.",
      copyright: '© 2026 <a href="https://mfairley.com" target="_blank" rel="noopener">Michael Fairley</a>',
    },
  },
});
