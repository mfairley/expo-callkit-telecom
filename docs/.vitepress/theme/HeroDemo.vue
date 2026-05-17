<template>
  <div class="hero-demo">
    <video
      class="hero-demo__video"
      :poster="withBase('/outgoing-call-ios-poster.jpg')"
      autoplay
      muted
      loop
      playsinline
      preload="metadata"
      aria-label="Outgoing call demo on iOS — expo-callkit-telecom example app"
    >
      <source :src="withBase('/outgoing-call-ios.mp4')" type="video/mp4" />
    </video>
  </div>
</template>

<script setup lang="ts">
import { withBase } from "vitepress";
</script>

<style scoped>
.hero-demo {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
}

.hero-demo__video {
  width: auto;
  height: auto;
  max-width: 280px;
  max-height: 480px;
  border-radius: 28px;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.06),
    0 16px 40px rgba(0, 0, 0, 0.18);
}

@media (max-width: 960px) {
  .hero-demo__video {
    max-height: 420px;
  }
}

@media (max-width: 768px) {
  .hero-demo__video {
    max-width: 220px;
    max-height: 400px;
  }
}
</style>
