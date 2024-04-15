package zera.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import zera.hmdp.dto.UserDTO;
import zera.hmdp.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {


////        HttpSession session = request.getSession();
//        //1.获取请求头中的token
//        String token = request.getHeader("authorization");
//
//        if (StrUtil.isBlank(token)) {
//            response.setStatus(401);
//            return false;
//        }
//        /*
//        * 2.基于token获取redis中的用户
//        * */
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
//
////        Object user = session.getAttribute("user");
//
//        if (userMap.isEmpty()) {
//            response.setStatus(401);
//            return false;
//        }
//
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//
//        UserHolder.saveUser(userDTO);
//
//        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //现在只需判断 是否需要拦截
        if(UserHolder.getUser() == null){
            response.setStatus(401);
            return false;
        }
        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
