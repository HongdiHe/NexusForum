package com.hongdi.nexusforum.service;

//import com.hongdi.nexusforum.dao.LoginTicketMapper;
import com.hongdi.nexusforum.dao.UserMapper;
import com.hongdi.nexusforum.entity.LoginTicket;
import com.hongdi.nexusforum.entity.User;
import com.hongdi.nexusforum.util.CommunityConstant;
import com.hongdi.nexusforum.util.CommunityUtil;
import com.hongdi.nexusforum.util.MailClient;
import com.hongdi.nexusforum.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class UserService implements CommunityConstant {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MailClient mailClient;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private RedisTemplate redisTemplate;

//    @Autowired
////    private LoginTicketMapper loginTicketMapper;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    public User findUserById(int id) {
//        return userMapper.selectById(id);
        User user = getCache(id);
        if (user == null) {
            user = initCache(id);
        }
        return user;
    }

    /**
     * Batch load users by IDs to avoid N+1 query issues.
     * Uses the existing cache layer (Redis) for each user.
     *
     * @param ids collection of user IDs to load
     * @return Map of userId -> User
     */
    public Map<Integer, User> findUsersByIds(Collection<Integer> ids) {
        Map<Integer, User> userMap = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return userMap;
        }
        for (int id : ids) {
            if (id != 0 && !userMap.containsKey(id)) {
                User user = findUserById(id);
                if (user != null) {
                    userMap.put(id, user);
                }
            }
        }
        return userMap;
    }

    public Map<String, Object> register(User user) {
        Map<String, Object> map = new HashMap<>();

        // 空值处理
        if (user == null) {
            throw new IllegalArgumentException("参数不能为空!");
        }
        if (StringUtils.isBlank(user.getUsername())) {
            map.put("usernameMsg", "账号不能为空!");
            return map;
        }
        if (StringUtils.isBlank(user.getPassword())) {
            map.put("passwordMsg", "密码不能为空!");
            return map;
        }
        if (StringUtils.isBlank(user.getEmail())) {
            map.put("emailMsg", "邮箱不能为空!");
            return map;
        }

        // 验证账号
        User u = userMapper.selectByName(user.getUsername());
        if (u != null) {
            map.put("usernameMsg", "该账号已存在!");
            return map;
        }

        // 验证邮箱
        u = userMapper.selectByEmail(user.getEmail());
        if (u != null) {
            map.put("emailMsg", "该邮箱已被注册!");
            return map;
        }
        // 注册用户
        user.setSalt(CommunityUtil.generateUUID().substring(0, 5));
        user.setPassword(CommunityUtil.md5(user.getPassword() + user.getSalt()));
        user.setType(0);
        user.setStatus(0);
        user.setActivationCode(CommunityUtil.generateUUID());
        user.setHeaderUrl(String.format("https://api.dicebear.com/7.x/avataaars/svg?seed=%dt.png", ThreadLocalRandom.current().nextInt(1000)));
        user.setCreateTime(new Date());
        userMapper.insertUser(user);

        //激活邮件
        Context context = new Context();
        context.setVariable("email", user.getEmail());
        // http://localhost:8080/community/activation/101/code
        String url = domain + contextPath + "/activation/" + user.getId() + "/" + user.getActivationCode();
        context.setVariable("url", url);
        String content = templateEngine.process("/mail/activation", context);
        mailClient.sendMail(user.getEmail(), "激活账号", content);

        return map;
    }

    /**
     * @throws
     * @title
     * @description 激活状态判断
     * @author He Hongdi
     * @updateTime 4/29/2020 3:40 PM
     */
    public int activation(int userId, String code) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ACTIVATION_FAILURE;
        }
        if (user.getStatus() == 1) {
            return ACTIVATION_REPEAT;
        } else if (Objects.equals(user.getActivationCode(), code)) {
            userMapper.updateStatus(userId, 1);//更改状态，变为已激活
            clearCache(userId);
            return ACTIVATION_SUCCESS;
        } else {
            return ACTIVATION_FAILURE;
        }
    }

    public Map<String, Object> login(String username, String password, int expiredSeconds) {
        Map<String, Object> map = new HashMap<>();

        // 空值处理
        if (StringUtils.isBlank(username)) {
            map.put("usernameMsg", "账号不能为空!");
            return map;
        }
        if (StringUtils.isBlank(password)) {
            map.put("passwordMsg", "密码不能为空!");
            return map;
        }

        // 验证账号
        User user = userMapper.selectByName(username);
        if (user == null) {
            map.put("usernameMsg", "该账号不存在!");
            return map;
        }

        // 验证状态
        if (user.getStatus() == 0) {
            map.put("usernameMsg", "该账号未激活!");
            return map;
        }

        // 验证密码 (BCrypt includes salt internally, so we pass raw password + legacy salt)
        if (!CommunityUtil.matchPassword(password + user.getSalt(), user.getPassword())) {
            map.put("passwordMsg", "密码不正确!");
            return map;
        }

        // 生成登录凭证
        LoginTicket loginTicket = new LoginTicket();
        loginTicket.setUserId(user.getId());
        loginTicket.setTicket(CommunityUtil.generateUUID());
        loginTicket.setStatus(0);
        loginTicket.setExpired(new Date(System.currentTimeMillis() + expiredSeconds * 1000L));

        //已使用redis替代loginTicketMapper
//        loginTicketMapper.insertLoginTicket(loginTicket);

        String ticketKey = RedisKeyUtil.getTicketKey(loginTicket.getTicket());
        redisTemplate.opsForValue().set(ticketKey, loginTicket);

        map.put("ticket", loginTicket.getTicket());
        return map;
    }

    public void logout(String ticket) {
//        loginTicketMapper.updateStatus(ticket, 1);
        //使用redis，更改ticket状态。不使用删除，为了后期进行登录相关的统计
        String ticketKey = RedisKeyUtil.getTicketKey(ticket);
        Object obj = redisTemplate.opsForValue().get(ticketKey);
        if (obj instanceof LoginTicket) {
            LoginTicket loginTicket = (LoginTicket) obj;
            loginTicket.setStatus(1);
            redisTemplate.opsForValue().set(ticketKey, loginTicket);
        }
    }

    public LoginTicket findLoginTicket(String ticket) {
        String ticketKey = RedisKeyUtil.getTicketKey(ticket);
//        return loginTicketMapper.selectByTicket(ticket);
        Object obj = redisTemplate.opsForValue().get(ticketKey);
        return obj instanceof LoginTicket ? (LoginTicket) obj : null;
    }

    public int updateHeader(int id, String headerUrl) {
        //先去更新，再去清理缓存。可以避免更新数据库失败，缓存消失的现象。现在：数据库更新失败，缓存不会清除
        int rows = userMapper.updateHeader(id, headerUrl);
        clearCache(id);
        return rows;
//        return userMapper.updateHeader(id, headerUrl);
    }

    public User findUserByName(String username) {
        return userMapper.selectByName(username);
    }

    //1.优先从缓存中取值
    public User getCache(int userId) {
        String userKey = RedisKeyUtil.getUserKey(userId);
        Object obj = redisTemplate.opsForValue().get(userKey);
        return obj instanceof User ? (User) obj : null;
    }

    //2.缓存取不到，从数据库中查，再存入缓存(初始化缓存)
    public User initCache(int userId) {
        User user = userMapper.selectById(userId);
        String userKey = RedisKeyUtil.getUserKey(userId);
        //过期时间1h，3600s
        redisTemplate.opsForValue().set(userKey, user, 3600, TimeUnit.SECONDS);
        return user;
    }

    //3.更新用户信息，清除缓存数据
    public void clearCache(int userId) {
        String userKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.delete(userKey);
    }

    /**
     * @Description: 根据用户类型，获得相应权限
     * @param userId
     * @return: java.util.Collection<? extends org.springframework.security.core.GrantedAuthority>
     * @Date 2020/5/20
     **/
    public Collection<? extends GrantedAuthority> getAuthorities(int userId){
        User user = userMapper.selectById(userId);

        List<GrantedAuthority> list=new ArrayList<>();
        list.add(new GrantedAuthority() {
            @Override
            public String getAuthority() {
                switch (user.getType()) {
                    case 1:
                        return AUTHORITY_ADMIN;
                    case 2:
                        return AUTHORITY_MODERATOR;
                    default:
                        return AUTHORITY_USER;
                }
            }
        });
        return list;
    }
}
