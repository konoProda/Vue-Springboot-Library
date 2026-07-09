package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.demo.entity.Book;
import com.example.demo.entity.BookWithUser;
import com.example.demo.entity.LendRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.BookMapper;
import com.example.demo.mapper.BookWithUserMapper;
import com.example.demo.mapper.LendRecordMapper;
import com.example.demo.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 借书/还书/续借 核心业务逻辑
 *
 * 将原本分散在 BookController、BookWithUserController、LendRecordController、
 * LendRecordController1 中的借阅相关操作收敛到本 Service，并通过 @Transactional
 * 保证多表操作的数据一致性。
 */
@Service
public class BorrowService {

    @Resource
    private BookMapper bookMapper;

    @Resource
    private LendRecordMapper lendRecordMapper;

    @Resource
    private BookWithUserMapper bookWithUserMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * 借书操作。
     * 在一个事务中完成:
     *   1. 校验图书是否可借 (status == "1")
     *   2. 校验用户当前借阅数量是否 < 5
     *   3. 更新 book.status = "0", book.borrownum + 1
     *   4. 插入 lend_record 记录
     *   5. 插入 bookwithuser 记录
     *
     * @param userId 读者 ID
     * @param bookId 图书 ID (对应 book 表主键)
     * @throws RuntimeException 图书不存在 / 不可借 / 借阅数量超限
     */
    @Transactional(rollbackFor = Exception.class)
    public void borrowBook(Long userId, Long bookId) {
        // ========== 1. 校验图书是否存在且可借 ==========
        Book book = bookMapper.selectById(bookId.intValue());
        if (book == null) {
            throw new RuntimeException("图书不存在");
        }
        if (!"1".equals(book.getStatus())) {
            throw new RuntimeException("该图书已被借出，暂不可借");
        }

        // ========== 2. 校验用户借阅数量 < 5 ==========
        LambdaQueryWrapper<BookWithUser> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(BookWithUser::getId, userId.intValue());
        Integer borrowCount = bookWithUserMapper.selectCount(countWrapper);
        if (borrowCount != null && borrowCount >= 5) {
            throw new RuntimeException("借阅数量已达上限(5本)，请先归还部分图书");
        }

        // ========== 3. 更新图书状态 ==========
        book.setStatus("0");
        book.setBorrownum(book.getBorrownum() != null ? book.getBorrownum() + 1 : 1);
        bookMapper.updateById(book);

        // ========== 4. 插入借阅历史记录 ==========
        LendRecord lendRecord = new LendRecord();
        lendRecord.setReaderId(userId.intValue());
        lendRecord.setIsbn(book.getIsbn());
        lendRecord.setBookname(book.getName());
        lendRecord.setLendTime(new Date());
        lendRecord.setStatus("0");       // "0" = 借阅中
        lendRecord.setBorrownum(book.getBorrownum());
        lendRecordMapper.insert(lendRecord);

        // ========== 5. 插入活跃借阅记录 ==========
        // 获取用户昵称
        User user = userMapper.selectById(userId.intValue());
        String nickName = (user != null && user.getNickName() != null) ? user.getNickName() : "";

        // 计算应还日期: 当前时间 + 30 天
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 30);

        BookWithUser bookWithUser = new BookWithUser();
        bookWithUser.setId(userId.intValue());
        bookWithUser.setIsbn(book.getIsbn());
        bookWithUser.setBookName(book.getName());
        bookWithUser.setNickName(nickName);
        bookWithUser.setLendtime(new Date());
        bookWithUser.setDeadtime(cal.getTime());
        bookWithUser.setProlong(2);      // 初始可续借 2 次
        bookWithUserMapper.insert(bookWithUser);
    }

    /**
     * 还书操作。
     * 在一个事务中完成:
     *   1. 更新 book.status = "1"
     *   2. 更新 lend_record 的 return_time 和 status = "1"
     *   3. 删除 bookwithuser 中对应的记录
     *
     * @param userId 读者 ID
     * @param bookId 图书 ID (对应 book 表主键)
     * @throws RuntimeException 图书不存在 / 未找到借阅记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void returnBook(Long userId, Long bookId) {
        // ========== 0. 查询图书 ==========
        Book book = bookMapper.selectById(bookId.intValue());
        if (book == null) {
            throw new RuntimeException("图书不存在");
        }

        // ========== 1. 更新图书状态为可借 ==========
        book.setStatus("1");
        bookMapper.updateById(book);

        // ========== 2. 更新借阅历史: 设置归还时间 + 状态 ==========
        UpdateWrapper<LendRecord> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("isbn", book.getIsbn())
                     .eq("reader_id", userId.intValue())
                     .eq("status", "0");   // 找到尚未归还的那条记录
        LendRecord lendRecord = new LendRecord();
        lendRecord.setReturnTime(new Date());
        lendRecord.setStatus("1");         // "1" = 已归还
        lendRecordMapper.update(lendRecord, updateWrapper);

        // ========== 3. 删除活跃借阅记录 ==========
        Map<String, Object> deleteMap = new HashMap<>();
        deleteMap.put("isbn", book.getIsbn());
        deleteMap.put("id", userId.intValue());
        bookWithUserMapper.deleteByMap(deleteMap);
    }

    /**
     * 续借操作。
     * 在一个事务中完成:
     *   1. 校验 bookwithuser 中该记录的 prolong > 0
     *   2. 将 deadtime 增加 30 天
     *   3. prolong 减 1
     *
     * @param userId 读者 ID
     * @param bookId 图书 ID (对应 book 表主键)
     * @throws RuntimeException 图书不存在 / 未找到借阅记录 / 续借次数已用完
     */
    @Transactional(rollbackFor = Exception.class)
    public void renewBook(Long userId, Long bookId) {
        // ========== 0. 查询图书 ==========
        Book book = bookMapper.selectById(bookId.intValue());
        if (book == null) {
            throw new RuntimeException("图书不存在");
        }

        // ========== 1. 查找活跃借阅记录 ==========
        LambdaQueryWrapper<BookWithUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BookWithUser::getIsbn, book.getIsbn())
                    .eq(BookWithUser::getId, userId.intValue());
        BookWithUser bookWithUser = bookWithUserMapper.selectOne(queryWrapper);
        if (bookWithUser == null) {
            throw new RuntimeException("未找到借阅记录，无法续借");
        }

        // ========== 2. 校验续借次数 ==========
        if (bookWithUser.getProlong() == null || bookWithUser.getProlong() <= 0) {
            throw new RuntimeException("续借次数已用完");
        }

        // ========== 3. 更新 deadtime (+30天) 和 prolong (-1) ==========
        Calendar cal = Calendar.getInstance();
        cal.setTime(bookWithUser.getDeadtime());
        cal.add(Calendar.DAY_OF_MONTH, 30);

        bookWithUser.setDeadtime(cal.getTime());
        bookWithUser.setProlong(bookWithUser.getProlong() - 1);

        UpdateWrapper<BookWithUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("isbn", book.getIsbn())
                     .eq("id", userId.intValue());
        bookWithUserMapper.update(bookWithUser, updateWrapper);
    }
}
