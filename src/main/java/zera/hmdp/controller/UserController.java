package zera.hmdp.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import zera.hmdp.dto.LoginFormDTO;
import zera.hmdp.dto.Result;
import zera.hmdp.dto.UserDTO;
import zera.hmdp.entity.User;
import zera.hmdp.service.IUserInfoService;
import zera.hmdp.service.IUserService;
import zera.hmdp.utils.UserHolder;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;


    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session){
        return userService.sendCode(phone,session);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm,session);
    }

}
