package world.michi.masker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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


//    @Async("asyncServiceExecutor")
//    public void in(){
//
//        if(maskerQueue.size() == 10000){
//
//            return;
//        }
//
//         Optional.ofNullable(stringRedisTemplate.opsForList().rightPop("MASKER", 10, TimeUnit.SECONDS)).ifPresent(str -> {
//
//             maskerQueue.add(MaskerQueue.MaskerMessageEntity.builder().time(Long.valueOf(str)).build());
//         });
//    }
//
//    @Async("asyncServiceExecutor")
//    public void out(){
//
//        maskerQueue.take((str) -> log.info("~" + str + "~" + (str.getTime() - System.currentTimeMillis())));
//
//    }


//    @Async("asyncServiceExecutor")
//    public void in(){
//
//        Optional.ofNullable(stringRedisTemplate.opsForList().rightPop("MASKER", 10, TimeUnit.SECONDS)).ifPresent(str -> {
//
//             maskerQueue.add(Long.valueOf(str));
//         });
//    }
//
//
//    @Async("asyncServiceExecutor")
//    public void out(){
//
//        maskerQueue.take(id -> {
//
//            long delay = id - System.currentTimeMillis();
//
//            if(delay < -10){
//
//                log.info("死信~" + id + "~" + delay);
//            }else{
//
//                log.info("~" + id + "~" + delay);
//            }
//
//        });
//
//    }


    private final Lock lock = new ReentrantLock();

    private Condition notNow = lock.newCondition();

    private Condition notEnough = lock.newCondition();

    @Async("asyncServiceExecutor")
    public void in(){

        Optional.ofNullable(stringRedisTemplate.opsForList().rightPop("M", 10, TimeUnit.SECONDS)).ifPresent(str -> {

            String[] strings = str.split(":");

            stringRedisTemplate.opsForZSet().add("T", strings[0], Long.valueOf(strings[1]));

            lock.lock();

            try {

                notNow.signalAll();

                notEnough.signalAll();

            } finally {

                lock.unlock();
            }

        });

    }


    public void out(){

        lock.lock();

        try {

            if (!stringRedisTemplate.hasKey("T")) {

                notEnough.await();

            } else {

                stringRedisTemplate.opsForZSet().rangeWithScores("T", 0, 1).forEach(stringTypedTuple -> {

                    try {

                        notNow.await((long) (stringTypedTuple.getScore() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);

                        if (System.currentTimeMillis() - stringTypedTuple.getScore() >= 0) {

                            if (System.currentTimeMillis() - stringTypedTuple.getScore() <= 10) {

                                success(stringTypedTuple.getValue(), (long) ((double) stringTypedTuple.getScore()));
                            } else {

                                fail(stringTypedTuple.getValue(), (long) ((double) stringTypedTuple.getScore()));
                            }
                            stringRedisTemplate.opsForZSet().remove("T", stringTypedTuple.getValue());
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {

            lock.unlock();
        }


    }


    @Async("asyncServiceExecutor")
    void success(String id, long time) {

        log.info("成功~" + id + "~" + (System.currentTimeMillis() - time));

    }


    @Async("asyncServiceExecutor")
    void fail(String id, long time) {

        log.info("失败~" + id + "~" + (System.currentTimeMillis() - time));

    }

}
