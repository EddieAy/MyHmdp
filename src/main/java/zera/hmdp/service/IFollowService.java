package zera.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.Follow;

public interface IFollowService extends IService<Follow> {
    Result follow(Long id, Boolean isFollow);

    Result isFollow(Long id);

    Result followCommons(Long id);
}
