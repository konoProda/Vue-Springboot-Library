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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 借书/还书/续借 核心业务逻辑。
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

    @Resource
    private OperationLogService operationLogService;

    @Value("${borrow.max-count:5}")
    private int maxBorrowCount;

    @Value("${borrow.duration-days:30}")
    private int borrowDurationDays;

    @Value("${borrow.renew-duration-days:30}")
    private int renewDurationDays;

    @Value("${borrow.max-renew-count:1}")
    private int maxRenewCount;

    // ==================== 借书 ====================

    @Transactional(rollbackFor = Exception.class)
    public void borrowBook(Long userId, Long bookId) {
        Book book = validateBorrowPreconditions(userId, bookId);
        executeBorrow(book, userId);
    }

    /**
     * 校验借书前置条件：图书存在/有库存、用户未超借阅上限、未重复借阅。
     * @return 校验通过后的 Book 对象（已包含最新字段值）
     */
    private Book validateBorrowPreconditions(Long userId, Long bookId) {
        // 1. 校验图书是否存在且可借
        Book book = bookMapper.selectById(bookId.intValue());
        if (book == null) {
            throw new RuntimeException("图书不存在");
        }
        if (book.getAvailableCopies() == null || book.getAvailableCopies() <= 0) {
            throw new RuntimeException("库存不足，暂无可用副本");
        }

        // 2. 校验是否有逾期未还图书
        LambdaQueryWrapper<BookWithUser> overdueWrapper = new LambdaQueryWrapper<>();
        overdueWrapper.eq(BookWithUser::getUserId, userId.intValue())
                      .lt(BookWithUser::getDeadtime, new Date());
        if (bookWithUserMapper.selectCount(overdueWrapper) > 0) {
            throw new RuntimeException("存在逾期未还图书，请先归还后再借阅");
        }

        // 3. 校验用户借阅数量 < maxBorrowCount
        LambdaQueryWrapper<BookWithUser> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(BookWithUser::getUserId, userId.intValue());
        Integer borrowCount = bookWithUserMapper.selectCount(countWrapper);
        if (borrowCount != null && borrowCount >= maxBorrowCount) {
            throw new RuntimeException("借阅数量已达上限(" + maxBorrowCount + "本)，请先归还部分图书");
        }

        // 3. 校验用户是否已借阅该书（防重复借阅）
        LambdaQueryWrapper<BookWithUser> duplicateWrapper = new LambdaQueryWrapper<>();
        duplicateWrapper.eq(BookWithUser::getUserId, userId.intValue())
                        .eq(BookWithUser::getIsbn, book.getIsbn());
        if (bookWithUserMapper.selectCount(duplicateWrapper) > 0) {
            throw new RuntimeException("不可重复借阅同一本书");
        }

        return book;
    }

    /**
     * 执行借书：更新库存 → 插入 lend_record → 插入 bookwithuser → 记录日志。
     */
    private void executeBorrow(Book book, Long userId) {
        // 1. 更新库存和借阅次数（乐观锁）
        book.setAvailableCopies(book.getAvailableCopies() - 1);
        book.setBorrownum(book.getBorrownum() != null ? book.getBorrownum() + 1 : 1);
        int updated = bookMapper.updateById(book);
        if (updated == 0) {
            throw new RuntimeException("借书失败：图书信息已被其他操作修改，请刷新后重试");
        }

        // 2. 插入借阅历史记录
        insertLendRecord(book, userId);

        // 3. 插入活跃借阅记录
        String nickName = insertBookWithUser(book, userId);

        // 4. 记录操作日志
        Map<String, Object> logDetail = new HashMap<>();
        logDetail.put("isbn", book.getIsbn());
        logDetail.put("bookName", "《" + book.getName() + "》");
        logDetail.put("borrownum", book.getBorrownum());
        operationLogService.log(userId, nickName,
                getRole(userId), "BORROW", logDetail);
    }

    private void insertLendRecord(Book book, Long userId) {
        LendRecord lendRecord = new LendRecord();
        lendRecord.setReaderId(userId.intValue());
        lendRecord.setIsbn(book.getIsbn());
        lendRecord.setBookname(book.getName());
        lendRecord.setLendTime(new Date());
        lendRecord.setStatus("0");
        lendRecord.setBorrownum(book.getBorrownum());
        lendRecordMapper.insert(lendRecord);
    }

    private String insertBookWithUser(Book book, Long userId) {
        User user = userMapper.selectById(userId.intValue());
        String nickName = (user != null && user.getNickName() != null) ? user.getNickName() : "";

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, borrowDurationDays);

        BookWithUser bookWithUser = new BookWithUser();
        bookWithUser.setUserId(userId.intValue());
        bookWithUser.setIsbn(book.getIsbn());
        bookWithUser.setBookName(book.getName());
        bookWithUser.setNickName(nickName);
        bookWithUser.setLendtime(new Date());
        bookWithUser.setDeadtime(cal.getTime());
        bookWithUser.setProlong(maxRenewCount);
        bookWithUserMapper.insert(bookWithUser);

        return nickName;
    }

    // ==================== 还书 ====================

    @Transactional(rollbackFor = Exception.class)
    public void returnBook(Long userId, Long bookId) {
        Book book = bookMapper.selectById(bookId.intValue());
        if (book == null) {
            throw new RuntimeException("图书不存在");
        }

        // 归还副本（乐观锁）
        book.setAvailableCopies(book.getAvailableCopies() != null
                ? book.getAvailableCopies() + 1 : 1);
        int updated = bookMapper.updateById(book);
        if (updated == 0) {
            throw new RuntimeException("还书失败：图书信息已被其他操作修改，请刷新后重试");
        }

        // 更新借阅历史
        UpdateWrapper<LendRecord> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("isbn", book.getIsbn())
                     .eq("reader_id", userId.intValue())
                     .eq("status", "0");
        LendRecord lendRecord = new LendRecord();
        lendRecord.setReturnTime(new Date());
        lendRecord.setStatus("1");
        lendRecordMapper.update(lendRecord, updateWrapper);

        // 删除活跃借阅记录
        Map<String, Object> deleteMap = new HashMap<>();
        deleteMap.put("isbn", book.getIsbn());
        deleteMap.put("user_id", userId.intValue());
        bookWithUserMapper.deleteByMap(deleteMap);

        // 日志
        User returnUser = userMapper.selectById(userId.intValue());
        String nickName = (returnUser != null && returnUser.getNickName() != null) ? returnUser.getNickName() : "";
        Map<String, Object> logDetail = new HashMap<>();
        logDetail.put("isbn", book.getIsbn());
        logDetail.put("bookName", "《" + book.getName() + "》");
        operationLogService.log(userId, nickName,
                returnUser != null ? returnUser.getRole() : null, "RETURN", logDetail);
    }

    // ==================== 续借 ====================

    @Transactional(rollbackFor = Exception.class)
    public void renewBook(Long userId, Long bookId) {
        Book book = bookMapper.selectById(bookId.intValue());
        if (book == null) {
            throw new RuntimeException("图书不存在");
        }

        LambdaQueryWrapper<BookWithUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BookWithUser::getIsbn, book.getIsbn())
                    .eq(BookWithUser::getUserId, userId.intValue());
        BookWithUser bookWithUser = bookWithUserMapper.selectOne(queryWrapper);
        if (bookWithUser == null) {
            throw new RuntimeException("未找到借阅记录，无法续借");
        }

        if (bookWithUser.getDeadtime() != null && bookWithUser.getDeadtime().before(new Date())) {
            throw new RuntimeException("该图书已逾期，无法续借，请先归还");
        }

        if (bookWithUser.getProlong() == null || bookWithUser.getProlong() <= 0) {
            throw new RuntimeException("续借次数已用完");
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(bookWithUser.getDeadtime());
        cal.add(Calendar.DAY_OF_MONTH, renewDurationDays);

        bookWithUser.setDeadtime(cal.getTime());
        bookWithUser.setProlong(bookWithUser.getProlong() - 1);

        UpdateWrapper<BookWithUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("isbn", book.getIsbn())
                     .eq("user_id", userId.intValue());
        bookWithUserMapper.update(bookWithUser, updateWrapper);

        Map<String, Object> logDetail = new HashMap<>();
        logDetail.put("isbn", book.getIsbn());
        logDetail.put("bookName", "《" + book.getName() + "》");
        logDetail.put("newDeadtime", bookWithUser.getDeadtime());
        logDetail.put("remainingProlong", bookWithUser.getProlong());
        User renewUser = userMapper.selectById(userId.intValue());
        operationLogService.log(userId, bookWithUser.getNickName(),
                renewUser != null ? renewUser.getRole() : null, "RENEW", logDetail);
    }

    // ==================== 辅助方法 ====================

    private Integer getRole(Long userId) {
        User user = userMapper.selectById(userId.intValue());
        return user != null ? user.getRole() : null;
    }
}
