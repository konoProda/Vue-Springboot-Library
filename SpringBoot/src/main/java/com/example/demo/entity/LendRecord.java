package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@TableName("lend_record")
@Data
public class LendRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer readerId;
    private String isbn;
    private String bookname;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private Date lendTime;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private Date returnTime;
    private String status;
    private Integer borrownum;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String overdueStatus;   // 借阅状态: "正常" / "即将到期" / "已逾期" (仅status='0'时计算)

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer overdueDays;    // 逾期天数 (已逾期时 > 0)
}
