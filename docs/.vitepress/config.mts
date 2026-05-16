import { defineConfig } from "vitepress";

export default defineConfig({
  title: "expo-callkit-telecom",
  description:
    "CallKit + Jetpack Core-Telecom for Expo / React Native — native call UI, VoIP push, LiveKit-friendly audio. A modern react-native-callkeep alternative.",

  // Served from https://mfairley.github.io/expo-callkit-telecom/
  base: "/expo-callkit-telecom/",

  cleanUrls: true,
  lastUpdated: true,

  sitemap: {
    hostname: "https://mfairley.github.io/expo-callkit-telecom/",
  },

  head: [
    ["meta", { name: "theme-color", content: "#3c82f6" }],
    [
      "meta",
      {
        name: "keywords",
        content:
          "expo callkit, expo voip, react native callkit, react native callkeep alternative, jetpack core-telecom, react native incoming call, expo livekit calling, pushkit expo, fcm voip android react native, expo native module callkit",
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
        content: "expo-callkit-telecom — CallKit + Jetpack Core-Telecom for Expo",
      },
    ],
    [
      "meta",
      {
        property: "og:description",
        content:
          "Native call UI, VoIP push, LiveKit-friendly audio. A modern react-native-callkeep alternative.",
      },
    ],
    [
      "meta",
      {
        property: "og:url",
        content: "https://mfairley.github.io/expo-callkit-telecom/",
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
        content: "expo-callkit-telecom — CallKit + Jetpack Core-Telecom for Expo",
      },
    ],
    [
      "meta",
      {
        name: "twitter:description",
        content:
          "Native call UI, VoIP push, LiveKit-friendly audio. A modern react-native-callkeep alternative.",
      },
    ],
  ],

  themeConfig: {
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
      copyright: "© 2026 Michael Fairley",
    },
  },
});
