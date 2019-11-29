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

    String maskQueue() {

        return prefix + ":queue:";
    }

    @Async("asyncServiceExecutor")
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

                                log.info("~" + (System.currentTimeMillis() - stringTypedTuple.getScore()));

                                success(stringTypedTuple.getValue());

                            } else {

                                log.info("!" + (System.currentTimeMillis() - stringTypedTuple.getScore()));

                                fail(stringTypedTuple.getValue());
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



    void distribute(String str) {

        String[] strings = str.split(",");

        stringRedisTemplate.opsForZSet().add(maskSet(), strings[1], System.currentTimeMillis() + Long.valueOf(strings[0]));

        lock.lock();

        try {

            notNow.signalAll();

            notEnough.signalAll();

        } finally {

            lock.unlock();
        }
    }


    @Async("asyncServiceExecutor")
    void success(String str) {

        String[] strings = str.split(":");

        stringRedisTemplate.opsForList().leftPush(maskQueue() + strings[1], str);

    }


    @Async("asyncServiceExecutor")
    void fail(String str) {

        String[] strings = str.split(":");

        stringRedisTemplate.opsForList().leftPush(maskQueue() + strings[0], str);

    }

}
