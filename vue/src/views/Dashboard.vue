<template>
  <div>
    <!-- 数字卡片 -->
    <el-row :gutter="20">
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
    <div id="myTimer" style="margin-left: 15px; font-weight: 550;"></div>

    <!-- 近 7 天借阅趋势折线图 -->
    <div id="trend-chart" style="width: 100%; height: 380px; margin-top: 20px;"></div>

    <!-- 热门图书 TOP 5 柱状图 -->
    <div id="top-books-chart" style="width: 100%; height: 380px; margin-top: 20px;"></div>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { ElMessage } from "element-plus";
import request from "../utils/request";

export default {
  data() {
    return {
      cards: [
        { title: '已借阅', data: 0, icon: '#iconlend-record-pro' },
        { title: '总访问', data: 0, icon: '#iconvisit'   },
        { title: '图书数', data: 0, icon: '#iconbook-pro' },
        { title: '用户数', data: 0, icon: '#iconpopulation' }
      ],
      trendChart: null,
      topBooksChart: null
    }
  },
  mounted() {
    this.circleTimer()
    this.loadDashboard()
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize)
    if (this.trendChart) this.trendChart.dispose()
    if (this.topBooksChart) this.topBooksChart.dispose()
  },
  methods: {
    handleResize() {
      if (this.trendChart) this.trendChart.resize()
      if (this.topBooksChart) this.topBooksChart.resize()
    },

    loadDashboard() {
      // 1. 加载汇总数据
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

      // 2. 加载借阅趋势 + 渲染折线图
      request.get("/dashboard/trend").then(res => {
        if (res.code == 0) {
          const data = res.data
          this.trendChart = echarts.init(document.getElementById('trend-chart'))
          this.trendChart.setOption({
            title: { text: '近 7 天借阅趋势', left: 'center' },
            tooltip: { trigger: 'axis' },
            grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
            xAxis: {
              type: 'category',
              data: data.map(d => d.date),
              boundaryGap: false
            },
            yAxis: {
              type: 'value',
              minInterval: 1,
              name: '借阅量'
            },
            series: [{
              type: 'line',
              smooth: true,
              lineStyle: { color: '#5470c6', width: 2 },
              areaStyle: { color: 'rgba(84,112,198,0.15)' },
              data: data.map(d => d.count)
            }]
          })
        }
      })

      // 3. 加载热门图书 + 渲染横向柱状图
      request.get("/dashboard/top-books").then(res => {
        if (res.code == 0) {
          const data = res.data
          this.topBooksChart = echarts.init(document.getElementById('top-books-chart'))
          this.topBooksChart.setOption({
            title: { text: '热门图书 TOP 5', left: 'center' },
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
            grid: { left: '3%', right: '10%', bottom: '3%', containLabel: true },
            xAxis: {
              type: 'value',
              minInterval: 1,
              name: '借阅次数'
            },
            yAxis: {
              type: 'category',
              data: data.map(d => d.bookname).reverse(),
              inverse: true
            },
            series: [{
              type: 'bar',
              label: { show: true, position: 'right' },
              barWidth: '40%',
              data: data.map(d => d.count).reverse(),
              itemStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                  { offset: 0, color: '#5470c6' },
                  { offset: 1, color: '#91cc75' }
                ])
              }
            }]
          })
        }
      })

      window.addEventListener('resize', this.handleResize)
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
</style>
