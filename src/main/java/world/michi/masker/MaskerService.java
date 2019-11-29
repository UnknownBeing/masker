package world.michi.masker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private final Lock lock = new ReentrantLock();
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Value("${masker.prefix:masker}")
    String prefix;
    @Value("${masker.timeout:10}")
    int timeout;
    @Value("${masker.expired:10}")
    int expired;
    @Value("${masker.current}")
    int current;
    private Condition notNow = lock.newCondition();
    private Condition notEnough = lock.newCondition();

    String maskSet() {

        return prefix + ":set:" + current;
    }

    String maskMainQueue() {

        return prefix + ":main";
    }



    public void in() {

        Optional.ofNullable(stringRedisTemplate.opsForList().rightPop(maskMainQueue(), timeout, TimeUnit.SECONDS)).ifPresent(str -> distribute(str));

    }


    public void out() {

        lock.lock();

        try {

            if (!stringRedisTemplate.hasKey(maskSet())) {

                notEnough.await();

            } else {

                stringRedisTemplate.opsForZSet().rangeWithScores(maskSet(), 0, 1).forEach(stringTypedTuple -> {

                    try {

                        notNow.await((long) (stringTypedTuple.getScore() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);

                        if (System.currentTimeMillis() - stringTypedTuple.getScore() >= 0) {

                            if (System.currentTimeMillis() - stringTypedTuple.getScore() <= expired) {

                                success(stringTypedTuple.getValue(), (long) ((double) stringTypedTuple.getScore()));
                            } else {

                                fail(stringTypedTuple.getValue(), (long) ((double) stringTypedTuple.getScore()));
                            }
                            stringRedisTemplate.opsForZSet().remove(maskSet(), stringTypedTuple.getValue());
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
    void distribute(String str) {

        String[] strings = str.split(":");

        stringRedisTemplate.opsForZSet().add(maskSet(), strings[0], Long.valueOf(strings[1]));

        lock.lock();

        try {

            notNow.signalAll();

            notEnough.signalAll();

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
