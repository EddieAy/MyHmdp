package zera.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.Blog;
import zera.hmdp.entity.User;
import zera.hmdp.mapper.BlogMapper;
import zera.hmdp.service.IBlogService;
import org.springframework.stereotype.Service;
import zera.hmdp.service.IUserService;
import zera.hmdp.utils.SystemConstants;
import zera.hmdp.utils.UserHolder;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("博客不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
//获取登录用户
        Long userId = UserHolder.getUser().getId();
        String value = userId.toString();

        String key = "blog:liked:" + blog.getId();
        Boolean isLike = stringRedisTemplate.opsForSet().isMember(key, value);
        blog.setIsLike(BooleanUtil.isTrue(isLike));
    }

    @Override
    public Result likeBlog(Long id) {

        //获取登录用户
        Long userId = UserHolder.getUser().getId();

        String key = "blog:liked:" + id;
        String value = userId.toString();


        //判断当前用户是否点赞
        Boolean isLike = stringRedisTemplate.opsForSet().isMember(key, value);
        //如果未点赞
        if(BooleanUtil.isFalse(isLike)){
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key,value);
            }
        }else {
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,value);
            }
        }
        return null;
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
