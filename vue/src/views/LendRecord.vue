<template>
  <div class="page-layout">
    <div class="sidebar-toggle" @click="sidebarOpen=!sidebarOpen">
      <el-icon :size="18"><Menu /></el-icon>
    </div>
    <div class="search-sidebar" :class="{ collapsed: !sidebarOpen }">
      <div class="sidebar-title">{{ $t('lendRecord.sidebarTitle') }}</div>
      <el-form size="small" label-position="top">
        <el-form-item :label="$t('lendRecord.isbn')">
          <el-input v-model="search1" :placeholder="$t('lendRecord.isbn')" clearable />
        </el-form-item>
        <el-form-item :label="$t('lendRecord.bookName')">
          <el-input v-model="search2" :placeholder="$t('lendRecord.bookName')" clearable />
        </el-form-item>
        <el-form-item :label="$t('lendRecord.readerId')">
          <el-input v-model="search3" :placeholder="$t('lendRecord.readerId')" clearable />
        </el-form-item>
        <el-form-item :label="$t('lendRecord.statusFilter')">
          <el-select v-model="overdueFilter" clearable :placeholder="$t('common.all')" @change="load">
            <el-option :label="$t('common.overdueUnreturned')" value="1" />
            <el-option :label="$t('common.returnedOption')" value="2" />
            <el-option :label="$t('common.unreturnedOption')" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load" style="width:100%">{{ $t('lendRecord.search') }}</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" @click="clear" style="width:100%">{{ $t('lendRecord.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="content-area">
      <div class="content-header">
        <el-popconfirm :title="$t('lendRecord.confirmDelete')" @confirm="deleteBatch" v-if="user.role == 1">
          <template #reference>
            <el-button type="danger" size="mini">{{ $t('lendRecord.deleteBatch') }}</el-button>
          </template>
        </el-popconfirm>
      </div>
      <div class="table-wrap">
    <!-- 数据字段-->

    <el-table :data="tableData" stripe border="true" @selection-change="handleSelectionChange">
      <el-table-column v-if="user.role ==1"
                       type="selection"
                       width="55">
      </el-table-column>
      <el-table-column prop="isbn" :label="$t('lendRecord.isbn')" sortable />
      <el-table-column prop="bookname" :label="$t('lendRecord.bookName')" />
      <el-table-column prop="readerId" :label="$t('lendRecord.readerId')" sortable/>
      <el-table-column prop="lendTime" :label="$t('lendRecord.lendTime')" sortable/>
      <el-table-column prop="returnTime" :label="$t('lendRecord.returnTime')" sortable/>
      <el-table-column prop="status" :label="$t('lendRecord.status')" >
        <template v-slot="scope">
          <el-tag v-if="scope.row.status == 0" type="warning">{{ $t('lendRecord.unreturned') }}</el-tag>
          <el-tag v-else type="success">{{ $t('lendRecord.returned') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="user.role === 1" :label="$t('lendRecord.actions')" width="160">
        <template v-slot="scope">
          <div class="action-btns">
            <el-button type="primary" class="btn-edit" size="mini" @click="handleEdit(scope.row)">{{ $t('lendRecord.edit') }}</el-button>
            <el-popconfirm :title="$t('lendRecord.confirmDelete')" @confirm="handleDelete(scope.row)">
              <template #reference><el-button type="danger" size="mini">{{ $t('lendRecord.delete') }}</el-button></template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
    </el-table>

      </div>
      <div class="content-footer">
      <el-pagination
          v-model:currentPage="currentPage"
          :page-sizes="[5, 10, 20]"
          :page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
      >
      </el-pagination>


      <el-dialog v-model="dialogVisible" title="修改借阅记录" width="30%">
        <el-form :model="form" label-width="120px">
          <el-form-item label="记录 ID">
            <el-input v-model="form.id" disabled />
          </el-form-item>
          <el-form-item label="读者 ID">
            <el-input v-model="form.readerId" disabled />
          </el-form-item>
          <el-form-item label="图书">
            <el-input :value="form.isbn + ' ' + form.bookname" disabled />
          </el-form-item>
          <el-form-item label="借阅时间" >
            <el-date-picker
                v-model="form.lendTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
            >
            </el-date-picker>
          </el-form-item>
          <el-form-item label="归还时间" >

            <el-date-picker
                v-model="form.returnTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
            >
            </el-date-picker>

          </el-form-item>
          <el-form-item label="是否归还" prop="status">
            <el-radio v-model="form.status" label="0" @change="onStatusChange('0')">未归还</el-radio>
            <el-radio v-model="form.status" label="1" @change="onStatusChange('1')">已归还</el-radio>
          </el-form-item>
        </el-form>
        <template #footer>
      <span class="dialog-footer">
        <el-button type="danger" @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="save(form.id)">确 定</el-button>
      </span>
        </template>
      </el-dialog>
    </div>
  </div>
</div>

</template>

<script >

import request from "../utils/request";
import {ElMessage} from "element-plus";
import moment from "moment";
import { defineComponent, reactive, toRefs } from 'vue'

export default defineComponent({

  created(){
    let userStr = sessionStorage.getItem("user") ||"{}"
    this.user = JSON.parse(userStr)
    this.load()
  },
  mounted() {
    this._onResize = () => { this.sidebarOpen = window.innerWidth >= 1024 }
    window.addEventListener('resize', this._onResize)
    this._onResize()
  },
  beforeUnmount() {
    window.removeEventListener('resize', this._onResize)
  },
  name: 'LendRecord',
  methods: {
    handleSelectionChange(val){
      this.forms = val
    },
    deleteBatch(){
      if(!this.forms.length){
        ElMessage.warning("请选择数据！")
        return
      }
      request.post("/LendRecord/deleteRecords",this.forms).then(res =>{
        if(res.code === '0'){
          ElMessage.success("批量删除成功")
          this.load()
        }
        else {
          ElMessage.error(res.msg)
        }
      })
    },
    load(){
      // 读者只能查看自己的借阅记录
      const readerFilter = this.user.role == 2 ? this.user.id : this.search3
      request.get("/LendRecord",{
        params:{
          pageNum: this.currentPage,
          pageSize: this.pageSize,
          search1: this.search1,
          search2: this.search2,
          search3: readerFilter,
          overdueFilter: this.overdueFilter,
        }
      }).then(res =>{
        console.log(res)
        this.tableData = res.data.records
        this.total = res.data.total
      })
    },
    save(id){
      // 修改借阅记录：使用记录 ID 精确定位
      request.put("/LendRecord/" + id, this.form).then(res => {
        console.log(res)
        if (res.code == 0) {
          ElMessage({ message: '修改成功', type: 'success' })
        } else {
          ElMessage.error(res.msg)
        }
      }).catch(err => {
        console.error(err)
        ElMessage.error('修改失败，请稍后重试')
      }).finally(() => {
        this.load()
        this.dialogVisible = false
      })
    },
    clear(){
      this.search1 = ""
      this.search2 = ""
      this.search3 = ""
      this.overdueFilter = ""
      this.load()
    },
    handleEdit(row){
      this.form = JSON.parse(JSON.stringify(row))
      this.dialogVisible = true
    },
    onStatusChange(newStatus) {
      if (newStatus === '0') {
        this.form.returnTime = null   // 未归还 → 清空（null 才能被后端 Jackson 正确解析）
      } else {
        this.form.returnTime = moment().format('YYYY-MM-DD HH:mm:ss')  // 已归还 → 当前时间
      }
    },
    handleSizeChange(pageSize){
      this.pageSize = pageSize
      this.load()
    },
    handleCurrentChange(pageNum){
      this.pageNum = pageNum
      this.load()
    },
    handleDelete(row){
      const form3 = JSON.parse(JSON.stringify(row))
      request.post("LendRecord/deleteRecord",form3).then(res =>{
        console.log(res)
        if(res.code == 0 ){
          ElMessage.success("删除成功")
        }
        else
          ElMessage.error(res.msg)
        this.load()
      })
    },
    add(){
      this.dialogVisible2 = true
      this.form ={}
    }
  },

  setup() {
    const state = reactive({
      shortcuts: [
        {
          text: 'Today',
          value: new Date(),
        },
        {
          text: 'Yesterday',
          value: () => {
            const date = new Date()
            date.setTime(date.getTime() - 3600 * 1000 * 24)
            return date
          },
        },
        {
          text: 'A week ago',
          value: () => {
            const date = new Date()
            date.setTime(date.getTime() - 3600 * 1000 * 24 * 7)
            return date
          },
        },
      ],
      value1: '',
      value2: '',
      value3: '',
      defaultTime: new Date(2000, 1, 1, 12, 0, 0), // '12:00:00'
    })

    return toRefs(state)
  },
  data() {
    return {
      sidebarOpen: true,
      form: {},
      search1:'',
      search2:'',
      search3:'',
      overdueFilter:'',
      total:10,
      currentPage:1,
      pageSize: 10,
      tableData: [],
      user:{},
      dialogVisible : false,
      dialogVisible2: false

    }
  },

})
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
.table-wrap { flex:1; overflow:hidden; }
.table-wrap :deep(.el-table) { height:100%; display:flex; flex-direction:column; }
.table-wrap :deep(.el-table__inner-wrapper) { flex:1; overflow:hidden; display:flex; flex-direction:column; }
.table-wrap :deep(.el-table__body-wrapper) { flex:1; overflow:auto; }
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
