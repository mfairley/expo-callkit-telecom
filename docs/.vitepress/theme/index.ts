import DefaultTheme from "vitepress/theme";
import { h } from "vue";
import DemoGrid from "./DemoGrid.vue";
import HeroDemo from "./HeroDemo.vue";
import "./custom.css";

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component("DemoGrid", DemoGrid);
  },
  Layout() {
    return h(DefaultTheme.Layout, null, {
      "home-hero-image": () => h(HeroDemo),
    });
  },
};
