import DefaultTheme from "vitepress/theme";
import { useRoute } from "vitepress";
import { defineComponent, h, nextTick, onMounted, watch } from "vue";
import DemoGrid from "./DemoGrid.vue";
import HeroDemo from "./HeroDemo.vue";
import "./custom.css";

// VitePress renders home/page content inside `<div class="VPContent">` with no
// landmark role, so screen readers (and Lighthouse) can't find a main region.
// Stamp `role="main"` on it after each navigation.
const Layout = defineComponent({
  setup() {
    const route = useRoute();
    const setMainRole = () => {
      document.querySelector(".VPContent")?.setAttribute("role", "main");
    };
    onMounted(setMainRole);
    watch(() => route.path, () => nextTick(setMainRole));
    return () =>
      h(DefaultTheme.Layout, null, {
        "home-hero-image": () => h(HeroDemo),
      });
  },
});

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component("DemoGrid", DemoGrid);
  },
  Layout,
};
