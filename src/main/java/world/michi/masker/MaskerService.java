package world.michi.masker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * @ClassName MaskerService
 * @Author 17
 * @Date 2019/11/27 16:13
 * @Description ...
 **/

@Component
@Slf4j
public class MaskerService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    MaskerQueue maskerQueue;


    @Async("asyncServiceExecutor")
    public void in(){

         Optional.ofNullable(stringRedisTemplate.opsForList().rightPop("MASKER", 10, TimeUnit.SECONDS)).ifPresent(str -> {

             long id = Long.valueOf(str);

             maskerQueue.add(id);
         });
    }

    @Async("asyncServiceExecutor")
    public void out(){

        long id = maskerQueue.take();

        if(id != 0){

            execute(id);

        }
    }


    @Async("asyncServiceExecutor")
    public void execute(long id){

        log.info("~" + (System.currentTimeMillis() - id));

    }

}
