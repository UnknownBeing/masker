package world.michi.masker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @ClassName Masker
 * @Author 17
 * @Date 2019/11/29 20:22
 * @Description ...
 **/

@Component
@Slf4j
public class Masker {

    private final Lock lock = new ReentrantLock();

    private Condition notNow = lock.newCondition();

    private Condition isEmpty = lock.newCondition();

    private Condition notEnough = lock.newCondition();

    TreeSet<String> set = new TreeSet<>();

    @Value("${masker.prefix:masker}")
    String prefix;

    @Value("${masker.timeout:10}")
    int timeout;

    @Value("${masker.expired:10}")
    int expired;

    @Value("${masker.current}")
    int current;

    @Value("${masker.size:1000}")
    int size;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    String maskExecuting() {

        return prefix + ":executing:" + current;
    }

    String maskMainQueue() {

        return prefix + ":main";
    }

    String maskQueue() {

        return prefix + ":queue:";
    }

    long then(String str){

        return Long.valueOf(str.split(",")[0]) - System.currentTimeMillis();

    }


    @Async("asyncServiceExecutor")
    void toExecuting(String str){

        String[] strings = str.split(",");

        stringRedisTemplate.opsForZSet().add(maskExecuting(), str, Long.valueOf(strings[0]));
    }


    @Async("asyncServiceExecutor")
    public void in() {

        Optional.ofNullable(stringRedisTemplate.opsForList().rightPop(maskMainQueue(), timeout, TimeUnit.SECONDS)).ifPresent(str -> {

            lock.lock();

            try{

                if(set.size() == size){

                    notEnough.await();
                }

                set.add(str);

                isEmpty.signalAll();

                notNow.signalAll();

                toExecuting(str);

            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {

                lock.unlock();
            }


        });


    }


    public void out() {

        lock.lock();

        try{

            if(set.size() == 0) {

                isEmpty.await();

            }else {

                String str = set.first();

                if (then(str) >= 0) {

                    notNow.await(then(str), TimeUnit.MILLISECONDS);

                } else {

                    set.remove(str);

                    notEnough.signalAll();

                    if (then(str) >= -expired) {

                        log.info("~" + then(str));

                        success(str);

                    } else {

                        log.info("失败" + then(str));

                        fail(str);
                    }

                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {

            lock.unlock();
        }


    }


    public void reIn() {

        lock.lock();

        try{

            set.addAll(stringRedisTemplate.opsForZSet().range(maskExecuting(), 0, stringRedisTemplate.opsForZSet().size(maskExecuting())));

        } finally {

            lock.unlock();
        }

    }


    @Async("asyncServiceExecutor")
    void success(String str) {

        String[] strings = str.split(":");

        stringRedisTemplate.opsForList().leftPush(maskQueue() + strings[2], str);

        stringRedisTemplate.opsForZSet().remove(maskExecuting(), str);
    }


    @Async("asyncServiceExecutor")
    void fail(String str) {

        String[] strings = str.split(":");

        stringRedisTemplate.opsForList().leftPush(maskQueue() + strings[1], str);

        stringRedisTemplate.opsForZSet().remove(maskExecuting(), str);

    }

}
