<template>
  <div class="home" style="padding: 10px">
    <!-- 搜索区 -->
    <div style="margin: 10px 0;">
      <el-form inline="true" size="small">
        <el-form-item label="操作类型">
          <el-select v-model="searchType" placeholder="全部" clearable style="width: 130px">
            <el-option label="全部" value="" />
            <el-option label="借书" value="BORROW" />
            <el-option label="还书" value="RETURN" />
            <el-option label="续借" value="RENEW" />
            <el-option label="删除图书" value="DELETE_BOOK" />
            <el-option label="删除用户" value="DELETE_USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="searchUser" placeholder="请输入用户名" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load" size="mini">查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-button size="mini" type="danger" @click="clear">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" stripe border>
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column prop="username" label="操作人" width="100" />
      <el-table-column prop="userRole" label="角色" width="80">
        <template v-slot="scope">
          <el-tag :type="scope.row.userRole == 1 ? 'danger' : 'success'" size="small">
            {{ scope.row.userRole == 1 ? '管理员' : '读者' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operationType" label="操作类型" width="110">
        <template v-slot="scope">
          <el-tag :type="typeColor(scope.row.operationType)" size="small">
            {{ typeLabel(scope.row.operationType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="操作详情" min-width="300">
        <template v-slot="scope">
          {{ formatDetail(scope.row.detail) }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="操作时间" width="180" sortable />
    </el-table>

    <!-- 分页 -->
    <div style="margin: 10px 0">
      <el-pagination
          v-model:currentPage="currentPage"
          :page-sizes="[10, 20, 50]"
          :page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script>
import request from "../utils/request";

export default {
  name: "Log",
  data() {
    return {
      searchType: '',
      searchUser: '',
      dateRange: null,
      currentPage: 1,
      pageSize: 10,
      total: 0,
      tableData: [],
      typeMap: {
        BORROW: '借书',
        RETURN: '还书',
        RENEW: '续借',
        DELETE_BOOK: '删除图书',
        DELETE_USER: '删除用户'
      }
    }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      const params = {
        pageNum: this.currentPage,
        pageSize: this.pageSize,
        operationType: this.searchType,
        username: this.searchUser
      }
      if (this.dateRange && this.dateRange.length === 2) {
        params.startTime = this.dateRange[0]
        params.endTime = this.dateRange[1]
      }
      request.get("/operation-logs", { params }).then(res => {
        if (res.code == 0) {
          this.tableData = res.data.records
          this.total = res.data.total
        }
      })
    },
    clear() {
      this.searchType = ''
      this.searchUser = ''
      this.dateRange = null
      this.load()
    },
    typeLabel(type) {
      return this.typeMap[type] || type
    },
    typeColor(type) {
      const colors = {
        BORROW: 'success',
        RETURN: 'primary',
        RENEW: 'warning',
        DELETE_BOOK: 'danger',
        DELETE_USER: 'danger'
      }
      return colors[type] || 'info'
    },
    formatDetail(detail) {
      if (!detail) return '-'
      try {
        const obj = typeof detail === 'string' ? JSON.parse(detail) : detail
        // 返回关键字段，跳过内部 ID
        const parts = []
        if (obj.isbn) parts.push('ISBN: ' + obj.isbn)
        if (obj.bookName) parts.push('《' + obj.bookName + '》')
        if (obj.bookId) parts.push('图书ID: ' + obj.bookId)
        if (obj.bookIds) parts.push('图书IDs: ' + JSON.stringify(obj.bookIds))
        if (obj.deletedUserId) parts.push('用户ID: ' + obj.deletedUserId)
        if (obj.userIds) parts.push('用户IDs: ' + JSON.stringify(obj.userIds))
        if (obj.count) parts.push('数量: ' + obj.count)
        if (obj.borrownum != null) parts.push('借次: ' + obj.borrownum)
        return parts.join('  ') || JSON.stringify(obj)
      } catch (e) {
        return detail
      }
    },
    handleSizeChange(size) {
      this.pageSize = size
      this.load()
    },
    handleCurrentChange(page) {
      this.currentPage = page
      this.load()
    }
  }
}
</script>
