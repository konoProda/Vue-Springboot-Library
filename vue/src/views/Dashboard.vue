<template>
  <div class="dashboard-root">
    <!-- 搜索栏 -->
    <div class="search-section">
      <div class="search-box">
        <div class="search-title">馆藏书刊</div>
        <div class="search-row">
          <el-icon class="search-icon"><search /></el-icon>
          <input
            v-model="searchKeyword"
            type="text"
            class="search-input"
            placeholder="请输入你的搜索内容"
            @keyup.enter="doSearch"
            @input="searchError=''"
          />
          <span class="search-tag" @click="doSearch">馆藏资源</span>
        </div>
        <div v-if="searchError" class="search-error">{{ searchError }}</div>
      </div>
    </div>

    <!-- 数字卡片 -->
    <el-row :gutter="20" justify="center">
      <el-col :span="6" v-for="item in cards" :key="item.title">
        <el-card class="box-card">
          <div slot="header" class="clearfix">{{ item.title }}</div>
          <div class="text item">
            <svg class="icon" aria-hidden="true">
              <use :xlink:href="item.icon" style="width: 100px"></use>
            </svg>
            <span class="text">{{ item.data }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 时钟 -->
    <div id="myTimer" class="dashboard-clock"></div>

    <!-- 近 7 天借阅趋势折线图 -->
    <div id="trend-chart" class="chart-box"></div>

    <!-- 热门图书 TOP 5 柱状图 -->
    <div id="top-books-chart" class="chart-box"></div>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { ElMessage } from "element-plus";
import request from "../utils/request";

export default {
  data() {
    return {
      searchKeyword: '',
      searchError: '',
      cards: [
        { title: '已借阅', data: 0, icon: '#iconlend-record-pro' },
        { title: '总访问', data: 0, icon: '#iconvisit'   },
        { title: '图书数', data: 0, icon: '#iconbook-pro' },
        { title: '用户数', data: 0, icon: '#iconpopulation' }
      ],
      trendChart: null,
      topBooksChart: null,
      trendData: [],
      topBooksData: []
    }
  },
  mounted() {
    this.circleTimer()
    this.loadDashboard()
    if (this.$route.query.q) {
      this.searchKeyword = this.$route.query.q
    }
    // 监听深色模式切换，重新渲染图表
    this.darkObserver = new MutationObserver(() => {
      if (this.trendChart) this.renderTrendChart()
      if (this.topBooksChart) this.renderTopBooksChart()
    })
    this.darkObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize)
    if (this.darkObserver) this.darkObserver.disconnect()
    if (this.trendChart) this.trendChart.dispose()
    if (this.topBooksChart) this.topBooksChart.dispose()
  },
  methods: {
    handleResize() {
      if (this.trendChart) this.trendChart.resize()
      if (this.topBooksChart) this.topBooksChart.resize()
    },
    doSearch() {
      const kw = this.searchKeyword.trim()
      if (!kw) { this.searchError = ''; return }
      request.get("/book", { params: { search2: kw, pageSize: 1 } }).then(res => {
        if (res.code == 0 && res.data && res.data.total > 0) {
          this.searchError = ''
          this.$router.push({ path: '/book', query: { q: kw } })
          return
        }
        request.get("/book", { params: { search3: kw, pageSize: 1 } }).then(res2 => {
          if (res2.code == 0 && res2.data && res2.data.total > 0) {
            this.searchError = ''
            this.$router.push({ path: '/book', query: { q: kw } })
          } else {
            this.searchError = '未找到匹配的图书，请修改关键词后重试'
          }
        })
      }).catch(() => {
        this.searchError = '搜索请求失败，请稍后重试'
      })
    },
    loadDashboard() {
      request.get("/dashboard").then(res => {
        if (res.code == 0) {
          this.cards[0].data = res.data.lendRecordCount
          this.cards[1].data = res.data.visitCount
          this.cards[2].data = res.data.bookCount
          this.cards[3].data = res.data.userCount
        } else {
          ElMessage.error(res.msg)
        }
      })

      request.get("/dashboard/trend").then(res => {
        if (res.code == 0) {
          this.trendData = res.data
          this.trendChart = echarts.init(document.getElementById('trend-chart'))
          this.renderTrendChart()
        }
      })

      request.get("/dashboard/top-books").then(res => {
        if (res.code == 0) {
          this.topBooksData = res.data
          this.topBooksChart = echarts.init(document.getElementById('top-books-chart'))
          this.renderTopBooksChart()
        }
      })

      window.addEventListener('resize', this.handleResize)
    },
    renderTrendChart() {
      if (!this.trendChart || !this.trendData.length) return
      const isDark = document.documentElement.classList.contains('dark')
      this.trendChart.setOption({
        title: { text: '近 7 天借阅趋势', left: 'center', textStyle: { color: isDark ? '#cfd3dc' : '#303133' } },
        tooltip: { trigger: 'axis' },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: {
          type: 'category', data: this.trendData.map(d => d.date), boundaryGap: false,
          axisLine: { lineStyle: { color: isDark ? '#555' : '#333' } },
          axisLabel: { color: isDark ? '#909399' : '#333' }
        },
        yAxis: {
          type: 'value', minInterval: 1, name: '借阅量',
          nameTextStyle: { color: isDark ? '#909399' : '#333' },
          axisLabel: { color: isDark ? '#909399' : '#333' },
          splitLine: { lineStyle: { color: isDark ? '#363637' : '#e0e0e0' } }
        },
        series: [{ type: 'line', smooth: true, lineStyle: { color: '#5470c6', width: 2 }, areaStyle: { color: 'rgba(84,112,198,0.15)' }, data: this.trendData.map(d => d.count) }]
      }, true)
    },
    renderTopBooksChart() {
      if (!this.topBooksChart || !this.topBooksData.length) return
      const isDark = document.documentElement.classList.contains('dark')
      const data = this.topBooksData
      this.topBooksChart.setOption({
        title: { text: '热门图书 TOP 5', left: 'center', textStyle: { color: isDark ? '#cfd3dc' : '#303133' } },
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '3%', right: '10%', bottom: '3%', containLabel: true },
        xAxis: {
          type: 'value', minInterval: 1, name: '借阅次数',
          nameTextStyle: { color: isDark ? '#909399' : '#333' },
          axisLabel: { color: isDark ? '#909399' : '#333' },
          splitLine: { lineStyle: { color: isDark ? '#363637' : '#e0e0e0' } }
        },
        yAxis: { type: 'category', data: data.map(d => d.bookname).reverse(), inverse: true, axisLabel: { color: isDark ? '#909399' : '#333' } },
        series: [{
          type: 'bar', label: { show: true, position: 'right', color: isDark ? '#cfd3dc' : '#333' }, barWidth: '40%',
          data: data.map(d => d.count).reverse(),
          itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#5470c6' }, { offset: 1, color: '#91cc75' }]) }
        }]
      }, true)
    },
    circleTimer() {
      this.getTimer()
      setInterval(() => { this.getTimer() }, 1000)
    },
    getTimer() {
      var d = new Date()
      var t = d.toLocaleString()
      document.getElementById('myTimer').innerHTML = t
    }
  }
}
</script>

<style scoped>
.dashboard-root {
  padding: 28px 20px 0 20px;
}

/* ===== 搜索栏 ===== */
.search-section {
  display: flex;
  justify-content: center;
  margin: 8px 0 40px;
}
.search-box {
  width: 65%;
  max-width: 720px;
}
.search-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  text-align: left;
}
.search-row {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 24px;
  padding: 0 16px;
  height: 48px;
}
.search-icon {
  color: #909399;
  font-size: 18px;
  flex-shrink: 0;
}
.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  padding: 0 12px;
  font-size: 15px;
  color: #303133;
}
.search-input::placeholder {
  color: #c0c4cc;
}
.search-tag {
  flex-shrink: 0;
  background: #f0f2f5;
  color: #606266;
  padding: 4px 16px;
  border-radius: 16px;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
}
.search-tag:hover {
  background: #e0e3e9;
}
.search-error {
  color: #f56c6c;
  margin-top: 8px;
  font-size: 14px;
  text-align: left;
}

/* ===== 卡片 ===== */
.box-card {
  width: 80%;
  margin-bottom: 25px;
  margin-left: 10px;
}
.clearfix {
  text-align: center;
  font-size: 15px;
}
.text {
  text-align: center;
  font-size: 24px;
  font-weight: 700;
  vertical-align: super;
}
.icon {
  width: 50px;
  height: 50px;
  padding-top: 5px;
  padding-right: 10px;
}

/* ===== 时钟 ===== */
.dashboard-clock {
  margin-left: 15px;
  font-weight: 550;
  color: #303133;
}

/* ===== 图表 ===== */
.chart-box {
  width: 100%;
  height: 380px;
  margin-top: 20px;
}

/* ==================== 深色模式 ==================== */
html.dark .search-title {
  color: #cfd3dc;
}
html.dark .search-row {
  background: #262727;
  border-color: #363637;
}
html.dark .search-input {
  color: #e5eaf3;
}
html.dark .search-input::placeholder {
  color: #6c6e72;
}
html.dark .search-tag {
  background: #363637;
  color: #a3a6ad;
}
html.dark .search-tag:hover {
  background: #48494a;
}
html.dark .dashboard-clock {
  color: #cfd3dc;
}
html.dark .clearfix {
  color: #cfd3dc;
}
html.dark .text {
  color: #e5eaf3;
}
html.dark .box-card {
  /* card bg already handled by global.css */
}
</style>
