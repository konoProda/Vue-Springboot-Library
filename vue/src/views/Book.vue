<template>
  <div class="page-layout">
    <div class="sidebar-toggle" @click="sidebarOpen=!sidebarOpen">
      <el-icon :size="18"><Menu /></el-icon>
    </div>
    <div class="search-sidebar" :class="{ collapsed: !sidebarOpen }">
      <div class="sidebar-title">搜索条件</div>
      <el-form size="small" label-position="top">
        <el-form-item label="图书编号">
          <el-input v-model="search1" placeholder="请输入图书编号" clearable />
        </el-form-item>
        <el-form-item label="图书名称">
          <el-input v-model="search2" placeholder="请输入图书名称" clearable />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="search3" placeholder="请输入作者" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load" style="width:100%">查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" @click="clear" style="width:100%">重置</el-button>
        </el-form-item>
      </el-form>
      <div v-if="numOfOutDataBook!=0" style="margin-top:8px">
        <el-popconfirm confirm-button-text="查看" cancel-button-text="取消" title="您有图书已逾期，请尽快归还" @confirm="toLook">
          <template #reference>
            <el-button type="warning" style="width:100%">逾期通知</el-button>
          </template>
        </el-popconfirm>
      </div>
    </div>
    <div class="content-area">
      <div class="content-header">
        <el-button type="primary" @click="add" v-if="user.role == 1">上架</el-button>
        <el-popconfirm title="确认删除?" @confirm="deleteBatch" v-if="user.role == 1">
          <template #reference>
            <el-button type="danger" size="mini">批量删除</el-button>
          </template>
        </el-popconfirm>
      </div>
      <div class="table-wrap">
        <el-table :data="tableData" stripe border @selection-change="handleSelectionChange">
      <el-table-column v-if="user.role ==1"
                       type="selection"
                       width="55">
      </el-table-column>
      <el-table-column prop="isbn" label="图书编号" sortable />
      <el-table-column prop="name" label="图书名称" />
      <el-table-column prop="price" label="价格" sortable/>
      <el-table-column prop="author" label="作者" />
      <el-table-column prop="publisher" label="出版社" />
      <el-table-column prop="createTime" label="出版时间" sortable/>
      <el-table-column prop="availableCopies" label="库存" sortable width="120">
        <template v-slot="scope">
          <span v-if="scope.row.availableCopies > 0" style="color: #67c23a; font-weight: bold;">
            可借 {{ scope.row.availableCopies }}
          </span>
          <span v-else style="color: #f56c6c; font-weight: bold;">
            已借完
          </span>
          <span style="color: #909399;"> / 馆藏 {{ scope.row.totalCopies }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template v-slot="scope">
          <div class="action-btns">
            <el-button type="primary" class="btn-edit" size="mini" @click="handleEdit(scope.row)" v-if="user.role == 1">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="handleDelete(scope.row.id)" v-if="user.role == 1">
              <template #reference><el-button type="danger" size="mini">删除</el-button></template>
            </el-popconfirm>
            <el-button type="success" size="mini" @click="handlelend(scope.row.id,scope.row.isbn,scope.row.name,scope.row.borrownum,scope.row)" v-if="user.role == 2" :class="{ 'borrow-btn--disabled': scope.row.availableCopies <= 0 || (this.isbnArray.indexOf(scope.row.isbn)) != -1 }">借阅</el-button>
            <el-popconfirm title="确认还书?" @confirm="handlereturn(scope.row.id,scope.row.isbn,scope.row.borrownum)" v-if="user.role == 2" :disabled="(this.isbnArray.indexOf(scope.row.isbn)) == -1">
              <template #reference><el-button type="danger" size="mini" :disabled="(this.isbnArray.indexOf(scope.row.isbn)) == -1">还书</el-button></template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
    </el-table>
      </div>
<!--测试,通知对话框-->
    <el-dialog
        v-model="dialogVisible3"
        v-if="numOfOutDataBook!=0"
        title="逾期详情"
        width="50%"
        :before-close="handleClose"
    >
        <el-table :data="outDateBook" style="width: 100%">
          <el-table-column prop="isbn" label="图书编号" />
          <el-table-column prop="bookName" label="书名" />
          <el-table-column prop="lendtime" label="借阅日期" />
          <el-table-column prop="deadtime" label="截至日期" />
        </el-table>

      <template #footer>
      <span class="dialog-footer">
        <el-button type="primary" @click="dialogVisible3 = false"
        >确认</el-button>
      </span>
      </template>
    </el-dialog>
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

      <el-dialog v-model="dialogVisible" title="上架书籍" width="30%">
        <el-form :model="form" label-width="120px">

          <el-form-item label="图书编号">
            <el-input style="width: 80%" v-model="form.isbn"></el-input>
          </el-form-item>
          <el-form-item label="图书名称">
            <el-input style="width: 80%" v-model="form.name"></el-input>
          </el-form-item>
          <el-form-item label="价格">
            <el-input style="width: 80%" v-model="form.price"></el-input>
          </el-form-item>
          <el-form-item label="作者">
            <el-input style="width: 80%" v-model="form.author"></el-input>
          </el-form-item>
          <el-form-item label="出版社">
            <el-input style="width: 80%" v-model="form.publisher"></el-input>
          </el-form-item>
          <el-form-item label="出版时间">
            <div>
              <el-date-picker value-format="YYYY-MM-DD" type="date" style="width: 80%" clearable v-model="form.createTime" ></el-date-picker>
            </div>
          </el-form-item>
          <el-form-item label="馆藏数量">
            <el-input-number style="width: 80%" v-model="form.totalCopies" :min="1" />
          </el-form-item>
        </el-form>
        <template #footer>
      <span class="dialog-footer">
        <el-button type="danger" @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="save">确 定</el-button>
      </span>
        </template>
      </el-dialog>

      <el-dialog v-model="dialogVisible2" title="修改书籍信息" width="30%">
        <el-form :model="form" label-width="120px">

          <el-form-item label="图书编号">
            <el-input style="width: 80%" v-model="form.isbn"></el-input>
          </el-form-item>
          <el-form-item label="图书名称">
            <el-input style="width: 80%" v-model="form.name"></el-input>
          </el-form-item>
          <el-form-item label="价格">
            <el-input style="width: 80%" v-model="form.price"></el-input>
          </el-form-item>
          <el-form-item label="作者">
            <el-input style="width: 80%" v-model="form.author"></el-input>
          </el-form-item>
          <el-form-item label="出版社">
            <el-input style="width: 80%" v-model="form.publisher"></el-input>
          </el-form-item>
          <el-form-item label="出版时间">
            <div>
              <el-date-picker value-format="YYYY-MM-DD" type="date" style="width: 80%" clearable v-model="form.createTime" ></el-date-picker>
            </div>
          </el-form-item>
          <el-form-item label="馆藏数量">
            <el-input-number style="width: 80%" v-model="form.totalCopies" :min="1" />
          </el-form-item>
        </el-form>
        <template #footer>
      <span class="dialog-footer">
        <el-button type="danger" @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="save">确 定</el-button>
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
    // 从 Dashboard 搜索跳转过来时，自动填入关键词
    if (this.$route.query.q) {
      this.search2 = this.$route.query.q
    }
    this.load()
  },
  name: 'Book',
  methods: {
  // (this.isbnArray.indexOf(scope.row.isbn)) == -1
    handleSelectionChange(val){
      this.ids = val.map(v =>v.id)
    },
    deleteBatch(){
      if (!this.ids.length) {
        ElMessage.warning("请选择数据！")
        return
      }
      //  一个小优化，直接发送这个数组，而不是一个一个的提交删除
      request.post("/book/deleteBatch",this.ids).then(res =>{
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
      this.numOfOutDataBook =0;
      this.outDateBook =[];
      this.isbnArray = [];
      request.get("/book",{
        params:{
          pageNum: this.currentPage,
          pageSize: this.pageSize,
          search1: this.search1,
          search2: this.search2,
          search3: this.search3,
        }
      }).then(res =>{
        console.log(res)
        this.tableData = res.data.records
        this.total = res.data.total
      })
    //
      if(this.user.role == 2){
        request.get("/bookwithuser",{
          params:{
            pageNum: "1",
            pageSize: this.total,
            search1: "",
            search2: "",
            search3: this.user.id,
          }
        }).then(res =>{
          console.log(res)
          this.bookData = res.data.records
          this.number = this.bookData.length;
          // 重置数组，避免还书后残留旧数据
          this.isbnArray = []
          this.outDateBook = []
          this.numOfOutDataBook = 0
          var nowDate = new Date();
          for(let i=0; i< this.number; i++){
            this.isbnArray.push(this.bookData[i].isbn);
            let dDate = new Date(this.bookData[i].deadtime);
            if(dDate < nowDate){
              this.outDateBook.push({
                isbn:this.bookData[i].isbn,
                bookName : this.bookData[i].bookName,
                deadtime : this.bookData[i].deadtime,
                lendtime : this.bookData[i].lendtime,
              });
              this.numOfOutDataBook = this.numOfOutDataBook + 1;
            }
          }
          console.log("in load():" +this.numOfOutDataBook );
        })
      }
      //
    },
    clear(){
      this.search1 = ""
      this.search2 = ""
      this.search3 = ""
      this.load()
    },

    handleDelete(id){
      request.delete("book/" + id ).then(res =>{
        console.log(res)
        if(res.code == 0 ){
          ElMessage.success("删除成功")
        }
        else
          ElMessage.error(res.msg)
        this.load()
      })
    },
    handlereturn(id,isbn,bn){
      // 单一业务请求：只提交 isbn，后端统一完成所有校验和计算
      request.post("/return", { isbn: isbn }).then(res =>{
        console.log(res)
        if(res.code == 0){
          ElMessage({ message: '还书成功', type: 'success' })
        }
        else {
          ElMessage.error(res.msg)
        }
        this.load()
      })
    },
    handlelend(id,isbn,name,bn,row){
      if (row && this.isbnArray.indexOf(row.isbn) !== -1) {
        ElMessage.error("不可重复借阅同一本书")
        return
      }
      if (row && row.availableCopies <= 0) {
        ElMessage.error("该图书库存不足，无法借阅")
        return
      }
      if(this.number ==5){
        ElMessage.warning("您不能再借阅更多的书籍了")
        return;
      }
      if(this.numOfOutDataBook !=0){
        ElMessage.warning("在您归还逾期书籍前不能再借阅书籍")
        return;
      }
      // 单一业务请求：只提交 isbn，后端统一完成所有校验和计算
      request.post("/borrow", { isbn: isbn }).then(res =>{
        console.log(res)
        if(res.code == 0){
          ElMessage({ message: '借阅成功', type: 'success' })
        }
        else {
          ElMessage.error(res.msg)
        }
        this.load()
      })
    },
    add(){
      this.dialogVisible= true
      this.form ={ totalCopies: 1 }
    },
    save(){
      //ES6语法
      //地址,但是？IP与端口？+请求参数
      // this.form?这是自动保存在form中的，虽然显示时没有使用，但是这个对象中是有它的
      if(this.form.id){
        // 编辑已有图书：可借数 = 原可借数 + 馆藏数变化量（自动计算，不可手动编辑）
        const oldBook = this.tableData.find(b => b.id === this.form.id)
        const oldAvailable = oldBook ? oldBook.availableCopies : 0
        const oldTotal = oldBook ? oldBook.totalCopies : 1
        // 校验：馆藏总数不得小于当前已借出数量
        const newTotal = this.form.totalCopies != null ? this.form.totalCopies : 1
        const borrowed = oldTotal - oldAvailable
        if (newTotal < borrowed) {
          ElMessage.error(`馆藏总数不得小于当前已借出数量(${borrowed}本)`)
          return
        }
        const delta = newTotal - oldTotal
        this.form.availableCopies = Math.max(0, oldAvailable + delta)
        request.put("/book",this.form).then(res =>{
          console.log(res)
          if(res.code == 0){
            ElMessage({
              message: '修改书籍信息成功',
              type: 'success',
            })
          }
          else {
            ElMessage.error(res.msg)
          }

          this.load()
          this.dialogVisible2 = false
        })
      }
      else {
        this.form.borrownum = 0
        // 新书：可借数 = 馆藏数（自动同步，不可手动编辑）
        this.form.availableCopies = this.form.totalCopies || 1
        this.form.totalCopies = this.form.totalCopies || 1
        request.post("/book",this.form).then(res =>{
          console.log(res)
          if(res.code == 0){
            ElMessage.success('上架书籍成功')
          }
          else {
            ElMessage.error(res.msg)
          }
          this.load()
          this.dialogVisible = false
        })
      }

    },
    // formatter(row) {:formatter="formatter"
    //   return row.address
    // },

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
    toLook(){
      this.dialogVisible3 =true;
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
      total:10,
      currentPage:1,
      pageSize: 10,
      tableData: [],
      user:{},
      number:0,
      bookData:[],
      isbnArray:[],
      outDateBook:[],
      numOfOutDataBook: 0,
      dialogVisible3 : true,
    }
  },
}
</script>

<style scoped>
.borrow-btn--disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.borrow-btn--disabled .el-button {
  pointer-events: none;
}

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
