package zera.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import zera.hmdp.dto.LoginFormDTO;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {
    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sendCode(String phone,HttpSession session);
}
