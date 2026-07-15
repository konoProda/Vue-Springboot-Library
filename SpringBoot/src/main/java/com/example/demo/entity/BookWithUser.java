package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@TableName("bookwithuser")
@Data
public class BookWithUser {

    @TableId(type = IdType.AUTO)
    @JsonIgnore
    private Long id;                // 自增主键 (内部使用，前端不可见)

    @JsonProperty("id")             // 保持前端 JSON 兼容: "id" 字段仍映射为 userId
    private Integer userId;         // 读者 ID (原 id 字段，存储用户ID)

    private String isbn;
    private String bookName;
    private String nickName;

    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private Date lendtime;

    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private Date deadtime;

    private Integer prolong;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String status;          // 借阅状态: "正常" / "即将到期" / "已逾期"

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer overdueDays;    // 逾期天数 (已逾期时 > 0)
}
