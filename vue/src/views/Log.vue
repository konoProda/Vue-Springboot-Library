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
            <el-option label="新增图书" value="ADD_BOOK" />
            <el-option label="修改图书" value="EDIT_BOOK" />
            <el-option label="删除图书" value="DELETE_BOOK" />
            <el-option label="新增用户" value="ADD_USER" />
            <el-option label="修改用户" value="EDIT_USER" />
            <el-option label="删除用户" value="DELETE_USER" />
            <el-option label="修改借阅记录" value="EDIT_LEND_RECORD" />
            <el-option label="删除借阅记录" value="DELETE_LEND_RECORD" />
            <el-option label="修改借阅状态" value="EDIT_BOOKWITHUSER" />
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
      <el-table-column label="操作人" width="180">
        <template v-slot="scope">
          {{ scope.row.username }} (ID:{{ scope.row.userId }})
        </template>
      </el-table-column>
      <el-table-column prop="userRole" label="权限" width="80">
        <template v-slot="scope">
          <el-tag :type="scope.row.userRole == 1 ? 'danger' : 'info'" size="small">
            {{ scope.row.userRole == 1 ? '管理员' : '非管理员' }}
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
        ADD_BOOK: '新增图书',
        EDIT_BOOK: '修改图书',
        DELETE_BOOK: '删除图书',
        ADD_USER: '新增用户',
        EDIT_USER: '修改用户',
        DELETE_USER: '删除用户',
        EDIT_LEND_RECORD: '修改借阅记录',
        DELETE_LEND_RECORD: '删除借阅记录',
        EDIT_BOOKWITHUSER: '修改借阅状态'
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
        ADD_BOOK: 'success',
        EDIT_BOOK: '',
        DELETE_BOOK: 'danger',
        ADD_USER: 'success',
        EDIT_USER: '',
        DELETE_USER: 'danger',
        EDIT_LEND_RECORD: '',
        DELETE_LEND_RECORD: 'danger',
        EDIT_BOOKWITHUSER: ''
      }
      return colors[type] || 'info'
    },
    formatDetail(detail) {
      if (!detail) return '-'
      try {
        const obj = typeof detail === 'string' ? JSON.parse(detail) : detail
        const parts = []
        if (obj.isbn) parts.push(obj.isbn + (obj.bookName ? ' ' + obj.bookName : ''))
        else if (obj.bookName) parts.push(obj.bookName)
        if (obj.book && !obj.isbn) parts.push('' + obj.book)
        if (obj.newUsername) parts.push('用户: ' + obj.newUsername)
        if (obj.nickName && !obj.newUsername) parts.push('姓名: ' + obj.nickName)
        if (obj.deletedUserId) parts.push('被删用户ID: ' + obj.deletedUserId)
        if (obj.editedUserId) parts.push('被编辑用户ID: ' + obj.editedUserId)
        if (obj.totalCopies != null) parts.push('馆藏: ' + obj.totalCopies)
        if (obj.availableCopies != null) parts.push('可借: ' + obj.availableCopies)
        if (obj.borrownum != null) parts.push('借次: ' + obj.borrownum)
        if (obj.remainingProlong != null) parts.push('剩余续借: ' + obj.remainingProlong)
        if (obj.beforeStatus) parts.push('状态: ' + obj.beforeStatus + '→' + obj.afterStatus)
        if (obj.beforeReturnTime) parts.push('归还: ' + (obj.beforeReturnTime || '无') + '→' + (obj.afterReturnTime || '无'))
        if (obj.count) parts.push('共' + obj.count + '条')
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
