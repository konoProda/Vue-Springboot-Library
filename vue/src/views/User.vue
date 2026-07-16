<template>
  <div class="page-layout">
    <!-- 折叠按钮 -->
    <div class="sidebar-toggle" @click="sidebarOpen=!sidebarOpen">
      <el-icon :size="18"><Menu /></el-icon>
    </div>
    <!-- 左侧搜索面板 -->
    <div class="search-sidebar" :class="{ collapsed: !sidebarOpen }">
      <div class="sidebar-title">{{ $t('user.sidebarTitle') }}</div>
      <el-form size="small" label-position="top">
        <el-form-item :label="$t('user.readerId')">
          <el-input v-model="search1" :placeholder="$t('user.readerId')" clearable />
        </el-form-item>
        <el-form-item :label="$t('user.name')">
          <el-input v-model="search2" :placeholder="$t('user.name')" clearable />
        </el-form-item>
        <el-form-item :label="$t('user.phone')">
          <el-input v-model="search3" :placeholder="$t('user.phone')" clearable />
        </el-form-item>
        <el-form-item :label="$t('user.address')">
          <el-input v-model="search4" :placeholder="$t('user.address')" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load" style="width:100%">{{ $t('user.search') }}</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" @click="clear" style="width:100%">{{ $t('user.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </div>
    <!-- 右侧内容区 -->
    <div class="content-area">
      <div class="content-header">
        <el-button type="primary" @click="add" v-if="user.role == 1">{{ $t('user.addReader') }}</el-button>
        <el-popconfirm :title="$t('user.confirmDelete')" @confirm="deleteBatch" v-if="user.role == 1">
          <template #reference>
            <el-button type="danger" size="mini">{{ $t('user.deleteBatch') }}</el-button>
          </template>
        </el-popconfirm>
      </div>
      <div class="table-wrap">
        <el-table :data="tableData" stripe border @selection-change="handleSelectionChange">
          <el-table-column v-if="user.role==1" type="selection" width="55" />
          <el-table-column prop="id" :label="$t('user.readerId')" sortable />
          <el-table-column prop="username" :label="$t('user.username')" />
          <el-table-column prop="nickName" :label="$t('user.name')" />
          <el-table-column prop="phone" :label="$t('user.phone')" />
          <el-table-column :label="$t('user.sex')">
            <template v-slot="scope">{{ scope.row.sex === '男' ? $t('user.male') : scope.row.sex === '女' ? $t('user.female') : scope.row.sex }}</template>
          </el-table-column>
          <el-table-column prop="address" :label="$t('user.address')" />
          <el-table-column :label="$t('user.actions')" width="160">
            <template v-slot="scope">
              <div class="action-btns">
                <el-button type="primary" class="btn-edit" size="mini" @click="handleEdit(scope.row)">{{ $t('user.edit') }}</el-button>
                <el-popconfirm :title="$t('user.confirmDelete')" @confirm="handleDelete(scope.row.id)">
                  <template #reference>
                    <el-button type="danger" size="mini">{{ $t('user.delete') }}</el-button>
                  </template>
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

      <el-dialog v-model="dialogVisible" :title="form.id ? '编辑读者信息' : '新增读者'" width="30%">
        <el-form :model="form" label-width="120px">
          <el-form-item :label="$t('user.username')">
            <el-input style="width: 80%" v-model="form.username" :disabled="!!form.id"></el-input>
          </el-form-item>
          <el-form-item :label="$t('user.initPassword')" v-if="!form.id">
            <el-input style="width: 80%" v-model="form.password" type="password" :placeholder="$t('user.initPassword')"></el-input>
          </el-form-item>
          <el-form-item :label="$t('user.nickname')">
            <el-input style="width: 80%" v-model="form.nickName"></el-input>
          </el-form-item>
          <el-form-item :label="$t('user.phone')">
            <el-input style="width: 80%" v-model="form.phone"></el-input>
          </el-form-item>
          <el-form-item :label="$t('user.sex')">
            <div>
              <el-radio v-model="form.sex" label="男">{{ $t('user.male') }}</el-radio>
              <el-radio v-model="form.sex" label="女">{{ $t('user.female') }}</el-radio>
            </div>
          </el-form-item>
          <el-form-item :label="$t('user.address')">
            <el-input type="textarea" style="width: 80%" v-model="form.address"></el-input>
          </el-form-item>
        </el-form>
        <template #footer>
      <span class="dialog-footer">
        <el-button type="danger" @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ $t('common.confirm') }}</el-button>
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

export default {
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
  name: 'User',
  methods: {
    handleSelectionChange(val){
      this.ids = val.map(v => v.id)
    },
    deleteBatch(){
      if (!this.ids.length) {
        ElMessage.warning("请选择数据！")
        return
      }
      //  一个小优化，直接发送这个数组，而不是一个一个的提交删除
      request.post("/user/deleteBatch",this.ids).then(res =>{
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
      request.get("user/usersearch",{
        params:{
          pageNum: this.currentPage,
          pageSize: this.pageSize,
          search1: this.search1,
          search2: this.search2,
          search3: this.search3,
          search4: this.search4,
        }
      }).then(res =>{
        console.log(res)
        this.tableData = res.data.records
        this.total = res.data.total
      })
    },
    clear(){
      this.search1 = ""
      this.search2 = ""
      this.search3 = ""
      this.load()
    },

    handleDelete(id){
      request.delete("user/" + id ).then(res =>{
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
      this.dialogVisible= true
      this.form = { role: 2 }  // 新增时默认角色为普通读者
    },
    save(){
      if(this.form.id){
        request.put("/user",this.form).then(res =>{
          console.log(res)
          if(res.code == 0){
            ElMessage({
              message: '更新成功',
              type: 'success',
            })
          }
          else {
            ElMessage.error(res.msg)
          }

          this.load() //不知道为啥，更新必须要放在这里面
          this.dialogVisible = false
        })
      }
      else {
        request.post("/user",this.form).then(res =>{
          console.log(res)
          if(res.code == 0){
            ElMessage.success('添加成功')
          }
          else {
            ElMessage.error(res.msg)
          }
          this.load()
          this.dialogVisible = false
        })
      }

    },


    handleEdit(row){
      this.form = JSON.parse(JSON.stringify(row))
      this.dialogVisible = true
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
      dialogVisible : false,
      search1:'',
      search2:'',
      search3:'',
      search4:'',
      total:10,
      currentPage:1,
      pageSize: 10,
      tableData: [

      ],
      user:{},
      ids:[],
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
