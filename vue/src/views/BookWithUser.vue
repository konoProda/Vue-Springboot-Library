<template>
  <div class="home" style ="padding: 10px">
    <!-- 搜索-->
    <div style="margin: 10px 0;">

      <el-form inline="true" size="small" >
        <el-form-item label="图书编号" >
          <el-input v-model="search1" placeholder="请输入图书编号"  clearable>
            <template #prefix><el-icon class="el-input__icon"><search/></el-icon></template>
          </el-input>
        </el-form-item >
        <el-form-item label="图书名称" >
          <el-input v-model="search2" placeholder="请输入图书名称"  clearable>
            <template #prefix><el-icon class="el-input__icon"><search /></el-icon></template>
          </el-input>
        </el-form-item >
        <el-form-item label="借阅者" v-if="user.role == 1">
          <el-input v-model="search3" placeholder="请输入借阅者昵称"  clearable>
            <template #prefix><el-icon class="el-input__icon"><search /></el-icon></template>
          </el-input>
        </el-form-item >
        <el-form-item label="借阅状态" v-if="user.role == 1">
          <el-select v-model="overdueFilter" clearable placeholder="全部" @change="load" style="width:130px">
            <el-option label="逾期未还" value="1" />
          </el-select>
        </el-form-item >
        <el-form-item>
          <el-button type="primary" style="margin-left: 1%" @click="load" size="mini">查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-button size="mini"  type="danger" @click="clear">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <!-- 按钮-->
    <div style="margin: 10px 0;" >
      <el-popconfirm title="确认删除?" @confirm="deleteBatch" v-if="user.role == 1">
        <template #reference>
          <el-button type="danger" size="mini" >批量删除</el-button>
        </template>
      </el-popconfirm>
    </div>
    <!-- 数据字段-->
    <el-table :data="tableData" stripe border="true" @selection-change="handleSelectionChange">
      <el-table-column v-if="user.role ==1"
          type="selection"
          width="55">
      </el-table-column>
      <el-table-column prop="isbn" label="图书编号" sortable />
      <el-table-column prop="bookName" label="图书名称" />
      <el-table-column prop="nickName" label="借阅者" />
      <el-table-column prop="lendtime" label="借阅时间" />
      <el-table-column prop="deadtime" label="最迟归还日期" />
      <el-table-column label="借阅状态" width="140">
        <template v-slot="scope">
          <el-tag v-if="scope.row.status === '已逾期'" type="danger">已逾期 {{ scope.row.overdueDays }}天</el-tag>
          <el-tag v-else-if="scope.row.status === '即将到期'" type="warning">即将到期</el-tag>
          <el-tag v-else type="success">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="prolong" label="可续借次数" />
      <el-table-column fixed="right" label="操作" >
        <template v-slot="scope">
          <el-button type="primary" class="btn-edit" size="mini" @click ="handleEdit(scope.row)" v-if="user.role == 1">编辑</el-button>
          <el-popconfirm title="确认删除?" @confirm="handleDelete(scope.row) " v-if="user.role == 1">
            <template #reference>
              <el-button type="danger" size="mini" >删除</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm title="确认续借(续借一次延长30天)?" @confirm="handlereProlong(scope.row)" v-if="user.role == 2" :disabled="scope.row.prolong == 0">
            <template #reference>
              <el-button type="success" size="mini" :disabled="scope.row.prolong == 0" >续借</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm title="确认还书?" @confirm="handleReturn(scope.row)" v-if="user.role == 2">
            <template #reference>
              <el-button type="danger" size="mini" >还书</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <!--    分页-->
    <div style="margin: 10px 0">
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

      <el-dialog v-model="dialogVisible2" title="修改借阅信息" width="30%">
        <el-form :model="form" label-width="120px">

          <el-form-item label="图书编号">
            <el-input style="width: 80%" v-model="form.isbn"></el-input>
          </el-form-item>
          <el-form-item label="图书名称">
            <el-input style="width: 80%" v-model="form.bookName"></el-input>
          </el-form-item>
          <el-form-item label="借阅者">
            <el-input style="width: 80%" v-model="form.nickName"></el-input>
          </el-form-item>
          <el-form-item label="续借次数">
            <el-input style="width: 80%" v-model="form.prolong"></el-input>
          </el-form-item>
          <el-form-item label="应还日期">
            <el-date-picker style="width: 80%" v-model="form.deadtime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择应还日期" />
          </el-form-item>
        </el-form>
        <template #footer>
      <span class="dialog-footer">
        <el-button type="danger" @click="dialogVisible2 = false">取 消</el-button>
        <el-button type="primary" @click="save">确 定</el-button>
      </span>
        </template>
      </el-dialog>

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
