<template>
  <section class="demo-grid">
    <div class="demo-grid__row" v-for="row in rows" :key="row.platform">
      <h3 class="demo-grid__platform">{{ row.platform }}</h3>
      <div class="demo-grid__items">
        <figure
          class="demo-grid__item"
          v-for="item in row.items"
          :key="item.label"
        >
          <video
            v-if="item.type === 'video'"
            :src="withBase(item.src)"
            :poster="item.poster ? withBase(item.poster) : undefined"
            autoplay
            muted
            loop
            playsinline
            preload="metadata"
          />
          <img v-else :src="withBase(item.src)" :alt="item.label" loading="lazy" />
          <figcaption>{{ item.label }}</figcaption>
        </figure>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { withBase } from "vitepress";

type DemoItem =
  | { type: "video"; label: string; src: string; poster: string }
  | { type: "image"; label: string; src: string };

const rows: { platform: string; items: DemoItem[] }[] = [
  {
    platform: "iOS",
    items: [
      {
        type: "video",
        label: "Outgoing call",
        src: "/outgoing-call-ios.mp4",
        poster: "/outgoing-call-ios-poster.jpg",
      },
      {
        type: "image",
        label: "Incoming (banner)",
        src: "/incoming-call-banner-ios.png",
      },
      {
        type: "image",
        label: "Incoming (full screen)",
        src: "/incoming-call-fullscreen-ios.png",
      },
    ],
  },
  {
    platform: "Android",
    items: [
      {
        type: "video",
        label: "Outgoing call",
        src: "/outgoing-call-android.mp4",
        poster: "/outgoing-call-android-poster.jpg",
      },
      {
        type: "image",
        label: "Incoming (banner)",
        src: "/incoming-call-banner-android.png",
      },
      {
        type: "image",
        label: "Incoming (full screen)",
        src: "/incoming-call-fullscreen-android.png",
      },
    ],
  },
];
</script>

<style scoped>
.demo-grid {
  margin: 3rem 0 4rem;
}

.demo-grid__row + .demo-grid__row {
  margin-top: 3rem;
}

.demo-grid__platform {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 1.25rem 0;
  letter-spacing: -0.01em;
}

.demo-grid__items {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.75rem;
}

.demo-grid__item {
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.demo-grid__item img,
.demo-grid__item video {
  width: 100%;
  max-width: 220px;
  height: auto;
  display: block;
  border-radius: 24px;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.06),
    0 12px 32px rgba(0, 0, 0, 0.14);
  background: var(--vp-c-bg-soft);
}

.demo-grid__item figcaption {
  margin-top: 0.875rem;
  font-size: 0.875rem;
  color: var(--vp-c-text-2);
  text-align: center;
}

@media (max-width: 720px) {
  .demo-grid__items {
    grid-template-columns: repeat(2, 1fr);
    gap: 1.25rem;
  }
  .demo-grid__item img,
  .demo-grid__item video {
    max-width: 100%;
  }
}

@media (max-width: 420px) {
  .demo-grid__items {
    grid-template-columns: 1fr;
  }
}
</style>
