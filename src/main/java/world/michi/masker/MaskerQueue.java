package world.michi.masker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @ClassName MaskerQueue
 * @Author 17
 * @Date 2019/11/26 14:57
 * @Description ...
 **/


@Slf4j
@Component
public class MaskerQueue {


    private final Lock lock = new ReentrantLock();

    private Condition notNow = lock.newCondition();

    PriorityQueue<Long> longs = new PriorityQueue<>();

    public long take(){

            lock.lock();
            try {

                if(longs.size() != 0){

                    long then = longs.peek() - System.currentTimeMillis();

                    if(then >= 0){

                        notNow.await(then, TimeUnit.MILLISECONDS);

                    }else {

                        return longs.poll();
                    }

                }else {
                    notNow.await();
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }finally {
                lock.unlock();
            }

        return 0;
    }


    public void add(long l){

        lock.lock();
        try {

            this.longs.add(l + System.currentTimeMillis());

            notNow.signalAll();
        } finally {
            lock.unlock();
        }

    }



}
