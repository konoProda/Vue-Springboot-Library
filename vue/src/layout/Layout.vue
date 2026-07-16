<template>
  <div>
    <Header/>
    <router-view style="padding: 20px; min-height: calc(100vh - 96px);" />
  </div>
</template>

<script>
import Header from "../components/Header";
export default {
  name: "Layout",
  components: { Header }
}
</script>
