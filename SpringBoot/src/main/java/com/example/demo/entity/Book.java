package com.example.demo.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;


import java.math.BigDecimal;
import java.util.Date;

@TableName("book")
@Data

public class Book {

    @TableId (type = IdType.AUTO)
    private Integer id;
    private String isbn;
    private String name;
    private BigDecimal price;
    private String author;
    private Integer borrownum;
    private String publisher;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd")
    private Date createTime;

    // ---- 多副本字段（替换原 status） ----
    // TODO: 前端需要适配多副本 — 原 status 字段已移除
    //   Book.vue 中 status 列（第66-71行）、借阅按钮 disabled 判断（第80-83行）、
    //   handlelend/handlereturn 中的 form.status 赋值 需要改为 availableCopies > 0
    private Integer totalCopies;       // 总馆藏数
    private Integer availableCopies;   // 当前可借数量

    @Version
    private Integer version;           // 乐观锁版本号


}
