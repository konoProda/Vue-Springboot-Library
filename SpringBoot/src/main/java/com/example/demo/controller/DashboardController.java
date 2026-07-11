package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.LoginUser;
import com.example.demo.commom.Result;
import com.example.demo.entity.Book;
import com.example.demo.entity.LendRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.BookMapper;
import com.example.demo.mapper.LendRecordMapper;
import com.example.demo.mapper.UserMapper;

import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {
    @Resource
    private UserMapper userMapper;
    @Resource
    private LendRecordMapper lendRecordMapper;
    @Resource
    private BookMapper bookMapper;

    /**
     * 汇总统计：访问量、用户数、借阅记录数、图书数
     */
    @GetMapping
    public Result<?> dashboardrecords() {
        int visitCount = LoginUser.getVisitCount();
        QueryWrapper<User> queryWrapper1 = new QueryWrapper<>();
        int userCount = userMapper.selectCount(queryWrapper1);
        QueryWrapper<LendRecord> queryWrapper2 = new QueryWrapper<>();
        int lendRecordCount = lendRecordMapper.selectCount(queryWrapper2);
        QueryWrapper<Book> queryWrapper3 = new QueryWrapper<>();
        int bookCount = bookMapper.selectCount(queryWrapper3);
        Map<String, Object> map = new HashMap<>();
        map.put("visitCount", visitCount);
        map.put("userCount", userCount);
        map.put("lendRecordCount", lendRecordCount);
        map.put("bookCount", bookCount);
        return Result.success(map);
    }

    /**
     * 近 7 天借阅趋势：每天的新增借阅数量
     * 返回格式: [{ date: "2026-07-05", count: 3 }, ...]
     */
    @GetMapping("/trend")
    public Result<?> borrowTrend() {
        // 计算 7 天前的日期
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        Date sevenDaysAgo = cal.getTime();

        QueryWrapper<LendRecord> wrapper = new QueryWrapper<>();
        wrapper.select("DATE(lend_time) as date", "COUNT(*) as count")
               .ge("lend_time", sevenDaysAgo)
               .groupBy("DATE(lend_time)")
               .orderByAsc("date");

        List<Map<String, Object>> rows = lendRecordMapper.selectMaps(wrapper);

        // 补全缺失的日期（无借阅的日期填 0）
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_MONTH, -i);
            String dateStr = String.format("%tm-%td", day, day);  // MM-DD 格式
            long count = 0;
            for (Map<String, Object> row : rows) {
                String rowDate = row.get("date").toString();
                if (dateStr.equals(rowDate.substring(5))) {  // yyyy-MM-dd → 比较 MM-dd
                    count = ((Number) row.get("count")).longValue();
                    break;
                }
            }
            Map<String, Object> item = new HashMap<>();
            item.put("date", dateStr);
            item.put("count", count);
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 热门图书 TOP 5：借阅次数最多的前 5 本书
     * 返回格式: [{ bookname: "三体", count: 12 }, ...]
     */
    @GetMapping("/top-books")
    public Result<?> topBooks() {
        QueryWrapper<LendRecord> wrapper = new QueryWrapper<>();
        wrapper.select("bookname", "COUNT(*) as count")
               .groupBy("bookname")
               .orderByDesc("count")
               .last("LIMIT 5");

        List<Map<String, Object>> rows = lendRecordMapper.selectMaps(wrapper);
        return Result.success(rows);
    }
}
