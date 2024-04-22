package zera.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zera.hmdp.dto.LoginFormDTO;
import zera.hmdp.dto.Result;
import zera.hmdp.dto.UserDTO;
import zera.hmdp.entity.User;
import zera.hmdp.mapper.UserMapper;
import zera.hmdp.service.IUserService;
import zera.hmdp.utils.RedisConstants;
import zera.hmdp.utils.RegexUtils;
import zera.hmdp.utils.UserHolder;


import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static zera.hmdp.utils.RedisConstants.*;
import static zera.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/*
* 多台tomcat session 不共享 或者说 共享 会有延迟 用户体验很差
*
* 替代方案 需要：
*   1.数据共享
*   2.内存存储  session就是基于内存的
*   3.key value
*
*
* 如何修改：
*   1.  在登录 保存验证码的时候 保存到之前的session  也就是现在的redis
*       数据类型 本质是手机号码 是个数字 可以用string解决
*       key如何解决呢  可以直接用“code"吗？
*       答案是不行的，因为redis是共享的，大家都来访问code，不同的访问，不同的手机号
*       都使用code的话，就会覆盖
*       既然 每个key都需要不一样 那就可以用手机号来作为Key
*       唯一性
*
*       选手机号还有另外一个原因：
*       客户端需要能够携带这个key
*       之前使用session的时候，客户端把信息携带到cookie中，服务器可以直接从coockie的
*       session中取数据，然后查找对应
*       因此，验证的时候，刚好就需要用手机号从redis中获取到key
*
*   2.  保存用户到redis
*       首先value如何选择呢，有两种方式，一种是json序列化保存
*       第二种是hash结构
*       其次是 key   要唯一性 要客户端 能够携带这个Key
*       用手机号也可以  但是一般情况 用一个随机的token
*
*       要把生成的token 返回给客户端   客户端（浏览器 把这个token保存下来）
*
*
* */

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper,User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        String code = RandomUtil.randomNumbers(6);

//        session.setAttribute("code",code);
        //set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("发送短信验证码成功，验证码:{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        //修改成从redis 获取验证码
//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }

        User user = query().eq("phone", phone).one();

        if(user == null){
            user = createUserWithPhone(phone);
        }

        //现在保存到redis 中去
        /*
        * 1.随机生成token 替代原来tomcat帮你做的事 将生成的token
        * 2.将User 转为redis 的hash存储起来
        * 3.存储
        * 4.返回 token到客户端
        * */
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor(
                        (fieldName,fieldValue) -> fieldValue.toString()
                ));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth() - 1;
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth,true);
        return Result.ok();

    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();

        List<Long> results = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(results == null || results.isEmpty()){
            return Result.ok(0);
        }
        Long num = results.get(0);
        if(num == null || num  == 0){
            return Result.ok(0);
        }
        int count = 0;
        while (true){
            if ((num & 1) == 0) {
                break;
            }else {
                count++;
            }
            num >>>= 1;
        }
        return Result.ok(count);

    }

    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }


}
