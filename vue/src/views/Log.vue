<template>
  <div class="page-layout">
    <div class="sidebar-toggle" @click="sidebarOpen=!sidebarOpen">
      <el-icon :size="18"><Menu /></el-icon>
    </div>
    <div class="search-sidebar" :class="{ collapsed: !sidebarOpen }">
      <div class="sidebar-title">{{ $t('log.sidebarTitle') }}</div>
      <el-form size="small" label-position="top">
        <el-form-item :label="$t('log.opType')">
          <el-select v-model="searchType" :placeholder="$t('common.all')" clearable>
            <el-option :label="$t('common.all')" value="" />
            <el-option :label="$t('log.types.BORROW')" value="BORROW" />
            <el-option :label="$t('log.types.RETURN')" value="RETURN" />
            <el-option :label="$t('log.types.RENEW')" value="RENEW" />
            <el-option :label="$t('log.types.ADD_BOOK')" value="ADD_BOOK" />
            <el-option :label="$t('log.types.EDIT_BOOK')" value="EDIT_BOOK" />
            <el-option :label="$t('log.types.DELETE_BOOK')" value="DELETE_BOOK" />
            <el-option :label="$t('log.types.ADD_USER')" value="ADD_USER" />
            <el-option :label="$t('log.types.EDIT_USER')" value="EDIT_USER" />
            <el-option :label="$t('log.types.DELETE_USER')" value="DELETE_USER" />
            <el-option :label="$t('log.types.EDIT_LEND_RECORD')" value="EDIT_LEND_RECORD" />
            <el-option :label="$t('log.types.DELETE_LEND_RECORD')" value="DELETE_LEND_RECORD" />
            <el-option :label="$t('log.types.EDIT_BOOKWITHUSER')" value="EDIT_BOOKWITHUSER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('log.operator')">
          <el-input v-model="searchUser" :placeholder="$t('log.operator')" clearable />
        </el-form-item>
        <el-form-item :label="$t('log.startTime')">
          <el-date-picker v-model="startTime" type="datetime" :placeholder="$t('log.startTime')"
            value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item :label="$t('log.endTime')">
          <el-date-picker v-model="endTime" type="datetime" :placeholder="$t('log.endTime')"
            value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load" style="width:100%"> {{ $t('log.search') }} </el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" @click="clear" style="width:100%"> {{ $t('log.reset') }} </el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="content-area">
      <div class="table-wrap">
        <el-table :data="tableData" stripe border>
      <el-table-column type="index" :label="$t('log.idx')" width="60" />
      <el-table-column :label="$t('log.operator')" width="180">
        <template v-slot="scope">
          {{ scope.row.username }} (ID:{{ scope.row.userId }})
        </template>
      </el-table-column>
      <el-table-column prop="userRole" :label="$t('log.role')" width="80">
        <template v-slot="scope">
          <el-tag :type="scope.row.userRole == 1 ? 'danger' : 'info'" size="small">
            {{ scope.row.userRole == 1 ? $t('log.admin') : $t('log.nonAdmin') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operationType" :label="$t('log.opType')" width="110">
        <template v-slot="scope">
          <el-tag :type="typeColor(scope.row.operationType)" size="small">
            {{ typeLabel(scope.row.operationType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detail" :label="$t('log.detail')" min-width="300">
        <template v-slot="scope">
          {{ formatDetail(scope.row.detail) }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" :label="$t('log.opTime')" width="180" sortable />
    </el-table>

      </div>
      <div class="content-footer">
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
</div>
</template>

<script>
import request from "../utils/request";

export default {
  name: "Log",
  data() {
    return {
      sidebarOpen: true,
      searchType: '',
      searchUser: '',
      startTime: '',
      endTime: '',
      currentPage: 1,
      pageSize: 10,
      total: 0,
      tableData: [],
      typeMap: {}
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
      if (this.startTime) params.startTime = this.startTime
      if (this.endTime) params.endTime = this.endTime
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
      this.startTime = ''
      this.endTime = ''
      this.load()
    },
    typeLabel(type) {
      return this.$t('log.types.' + type) || type
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

<style scoped>
.page-layout { display:flex; height:calc(100vh - 96px); position:relative; }
.sidebar-toggle { display:none; position:absolute; top:8px; left:8px; z-index:20; cursor:pointer;
  background:#fff; border:1px solid #dcdfe6; border-radius:4px; padding:4px 8px; }
.search-sidebar { width:22%; min-width:200px; border-right:1px solid #e0e0e0; padding:16px;
  overflow-y:auto; background:#fafafa; flex-shrink:0; }
.search-sidebar.collapsed { display:none; }
.sidebar-title { font-weight:600; font-size:15px; margin-bottom:12px; color:#303133; }
.content-area { flex:1; display:flex; flex-direction:column; overflow:hidden; padding:12px 16px; }
.content-header { margin-bottom:10px; display:flex; gap:8px; }
.content-footer { margin-top:10px; }
.table-wrap { flex:1; overflow-y:auto; }
.action-btns { display:flex; gap:4px; flex-wrap:nowrap; white-space:nowrap; }

@media (max-width: 1024px) {
  .sidebar-toggle { display:block; }
  .search-sidebar:not(.collapsed) { position:absolute; left:0; top:0; height:100%; z-index:15;
    box-shadow:2px 0 8px rgba(0,0,0,0.15); width:240px; }
}

html.dark .search-sidebar { background:#1a1a1b; border-color:#363637; }
html.dark .sidebar-title { color:#cfd3dc; }
html.dark .sidebar-toggle { background:#262727; border-color:#363637; color:#cfd3dc; }
</style>
