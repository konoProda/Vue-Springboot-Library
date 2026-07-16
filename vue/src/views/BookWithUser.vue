<template>
  <div class="page-layout">
    <div class="sidebar-toggle" @click="sidebarOpen=!sidebarOpen">
      <el-icon :size="18"><Menu /></el-icon>
    </div>
    <div class="search-sidebar" :class="{ collapsed: !sidebarOpen }">
      <div class="sidebar-title">{{ $t('borrow.sidebarTitle') }}</div>
      <el-form size="small" label-position="top">
        <el-form-item :label="$t('borrow.isbn')">
          <el-input v-model="search1" :placeholder="$t('book.isbn')" clearable />
        </el-form-item>
        <el-form-item :label="$t('borrow.bookName')">
          <el-input v-model="search2" :placeholder="$t('book.name')" clearable />
        </el-form-item>
        <el-form-item :label="$t('borrow.borrower')" v-if="user.role == 1">
          <el-input v-model="search3" :placeholder="$t('borrow.borrower')" clearable />
        </el-form-item>
        <el-form-item :label="$t('borrow.overdueFilter')" v-if="user.role == 1">
          <el-select v-model="overdueFilter" clearable :placeholder="$t('common.all')" @change="load">
            <el-option :label="$t('common.overdueUnreturned')" value="1" />
            <el-option :label="$t('common.notOverdue')" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load" style="width:100%"> {{ $t('borrow.search') }} </el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" @click="clear" style="width:100%"> {{ $t('borrow.reset') }} </el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="content-area">
      <div class="content-header">
        <el-popconfirm :title="$t('borrow.confirmDelete')" @confirm="deleteBatch" v-if="user.role == 1">
          <template #reference>
            <el-button type="danger" size="mini"> {{ $t('borrow.deleteBatch') }} </el-button>
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
      <el-table-column prop="isbn" :label="$t('borrow.isbn')" sortable />
      <el-table-column prop="bookName" :label="$t('borrow.bookName')" />
      <el-table-column prop="nickName" :label="$t('borrow.borrower')" />
      <el-table-column prop="lendtime" :label="$t('borrow.lendTime')" />
      <el-table-column prop="deadtime" :label="$t('borrow.deadline')" />
      <el-table-column :label="$t('borrow.overdueFilter')" width="140">
        <template v-slot="scope">
          <el-tag v-if="scope.row.status === '已逾期'" type="danger"> {{ $t('borrow.overdue') }} {{ scope.row.overdueDays }}{{ $t('borrow.overdueDays') }} </el-tag>
          <el-tag v-else-if="scope.row.status === '即将到期'" type="warning"> {{ $t('borrow.dueSoon') }} </el-tag>
          <el-tag v-else type="success"> {{ $t('borrow.normal') }} </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="prolong" :label="$t('borrow.prolong')" />
      <el-table-column fixed="right" :label="$t('borrow.actions')" >
        <template v-slot="scope">
          <div class="action-btns">
            <el-button type="primary" class="btn-edit" size="mini" @click ="handleEdit(scope.row)" v-if="user.role == 1"> {{ $t('borrow.edit') }} </el-button>
            <el-popconfirm :title="$t('borrow.confirmDelete')" @confirm="handleDelete(scope.row) " v-if="user.role == 1">
              <template #reference><el-button type="danger" size="mini"> {{ $t('borrow.delete') }} </el-button></template>
            </el-popconfirm>
            <el-popconfirm :title="$t('borrow.confirmRenew')" @confirm="handlereProlong(scope.row)" v-if="user.role == 2" :disabled="scope.row.prolong == 0">
              <template #reference><el-button type="success" size="mini" :disabled="scope.row.prolong == 0"> {{ $t('borrow.renew') }} </el-button></template>
            </el-popconfirm>
            <el-popconfirm :title="$t('borrow.confirmReturn')" @confirm="handleReturn(scope.row)" v-if="user.role == 2">
              <template #reference><el-button type="danger" size="mini"> {{ $t('borrow.return') }} </el-button></template>
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

      <el-dialog v-model="dialogVisible2" :title="$t('borrow.editDialogTitle')" width="30%">
        <el-form :model="form" label-width="120px">

          <el-form-item :label="$t('borrow.isbn')">
            <el-input style="width: 80%" v-model="form.isbn"></el-input>
          </el-form-item>
          <el-form-item :label="$t('borrow.bookName')">
            <el-input style="width: 80%" v-model="form.bookName"></el-input>
          </el-form-item>
          <el-form-item :label="$t('borrow.borrower')">
            <el-input style="width: 80%" v-model="form.nickName"></el-input>
          </el-form-item>
          <el-form-item :label="$t('borrow.prolong')">
            <el-input style="width: 80%" v-model="form.prolong"></el-input>
          </el-form-item>
          <el-form-item :label="$t('borrow.deadlineEdit')">
            <el-date-picker style="width: 80%" v-model="form.deadtime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :placeholder="$t('borrow.deadlineEdit')" />
          </el-form-item>
        </el-form>
        <template #footer>
      <span class="dialog-footer">
        <el-button type="danger" @click="dialogVisible2 = false"> {{ $t('common.cancel') }} </el-button>
        <el-button type="primary" @click="save"> {{ $t('common.confirm') }} </el-button>
      </span>
        </template>
      </el-dialog>
    </div>
  </div>
</div>
</template>

<script>
// @ is an alias to /src
import request from "../utils/request";
import {ElMessage} from "element-plus";
import moment from "moment";
export default {
  created(){
    let userStr = sessionStorage.getItem("user") ||"{}"
    this.user = JSON.parse(userStr)
    this.load()
  },
  name: 'bookwithuser',
  methods: {

    handleSelectionChange(val){
      this.forms = val
    },
    deleteBatch(){
      if (!this.forms.length) {
        ElMessage.warning("请选择数据！")
        return
      }
    //  一个小优化，直接发送这个数组，而不是一个一个的提交删除
      request.post("bookwithuser/deleteRecords",this.forms).then(res =>{
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
      if(this.user.role == 1){
        request.get("/bookwithuser",{
          params:{
            pageNum: this.currentPage,
            pageSize: this.pageSize,
            search1: this.search1,
            search2: this.search2,
            search3: this.search3,
            overdueFilter: this.overdueFilter,
          }
        }).then(res =>{
          console.log(res)
          this.tableData = res.data.records
          this.total = res.data.total
        })
      }
      else {
        request.get("/bookwithuser",{
          params:{
            pageNum: this.currentPage,
            pageSize: this.pageSize,
            search1: this.search1,
            search2: this.search2,
            search3: this.user.id,
          }
        }).then(res =>{
          console.log(res)
          this.tableData = res.data.records
          this.total = res.data.total
        })
      }
    },
    clear(){
      this.search1 = ""
      this.search2 = ""
      this.search3 = ""
      this.overdueFilter = ""
      this.load()
    },
    handleDelete(row){
      request.post("bookwithuser/deleteRecords",[row]).then(res =>{
        console.log(res)
        if(res.code === '0'){
          ElMessage.success("删除成功")
        }
        else
          ElMessage.error(res.msg)
        this.load()
      })
    },
    handlereProlong(row){
      // 单一业务请求：只提交 isbn，后端统一完成校验和日期计算
      request.post("/renew", { isbn: row.isbn }).then(res =>{
        console.log(res)
        if(res.code == 0){
          ElMessage({ message: '续借成功', type: 'success' })
        }
        else {
          ElMessage.error(res.msg)
        }
        this.load()
        this.dialogVisible2 = false
      })
    },
    handleReturn(row){
      // 单一业务请求：只提交 isbn，后端统一完成所有校验和计算
      request.post("/return", { isbn: row.isbn }).then(res => {
        console.log(res)
        if (res.code == 0) {
          ElMessage({ message: '还书成功', type: 'success' })
        } else {
          ElMessage.error(res.msg)
        }
        this.load()
      })
    },
    save(){
      //ES6语法
      //地址,但是？IP与端口？+请求参数
      // this.form?这是自动保存在form中的，虽然显示时没有使用，但是这个对象中是有它的
        request.post("/bookwithuser",this.form).then(res =>{
          console.log(res)
          if(res.code == 0){
            ElMessage({
              message: '修改信息成功',
              type: 'success',
            })
          }
          else {
            ElMessage.error(res.msg)
          }
          this.load()
          this.dialogVisible2 = false
        })
    },

    handleEdit(row){
      this.form = JSON.parse(JSON.stringify(row))
      this.dialogVisible2 = true
    },
    handleSizeChange(pageSize){
      this.pageSize = pageSize
      this.load()
    },
    handleCurrentChange(pageNum){
      this.pageNum = pageNum
      this.load()
    },

  },
  data() {
    return {
      sidebarOpen: true,
      form: {},
      form2:{},
      form3:{},
      dialogVisible: false,
      dialogVisible2: false,
      search1:'',
      search2:'',
      search3:'',
      overdueFilter:'',
      total:10,
      currentPage:1,
      pageSize: 10,
      tableData: [],
      user:{},
      forms:[],
    }
  },
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
