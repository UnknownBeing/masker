package world.michi.masker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
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


//    DelayQueue<MaskerMessageEntity> queue = new DelayQueue();
//
//
//    @Data
//    @Builder
//    public static class MaskerMessageEntity implements Delayed {
//
//        private long time;
//
//
//        @Override
//        public long getDelay(TimeUnit unit) {
//            return unit.convert(time - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
//        }
//
//        @Override
//        public int compareTo(Delayed o) {
//            return (int) (this.getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS));
//        }
//    }
//
//
//
//    @FunctionalInterface
//    interface MaskerExecutor{
//        void execute(MaskerMessageEntity entity);
//    }
//
//
//    public void take(MaskerExecutor maskerExecutor){
//
//        try {
//            maskerExecutor.execute(this.queue.take());
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//
//    }
//
//
//    public void add(MaskerMessageEntity entity){
//
//        this.queue.add(entity);
//
//    }
//
//
//    public int size(){
//
//        return this.queue.size();
//    }


    private final Lock lock = new ReentrantLock();

    private Condition notNow = lock.newCondition();

    private Condition notEnough = lock.newCondition();

    PriorityQueue<String> queue = new PriorityQueue<>();


//    @FunctionalInterface
//    interface MaskerExecutor{
//        void execute(long id);
//    }
//
//
//
//
    public int size(){
        lock.lock();

        try {
            return this.queue.size();

        }finally {
            lock.unlock();
        }
    }

    public String last(){
        lock.lock();

        try {
            return (String) this.queue.toArray()[size() - 1];
        }finally {
            lock.unlock();
        }

    }

    public void remove(String str){

        lock.lock();

        try {
            this.queue.remove(str);
        }finally {
            lock.unlock();
        }

    }

    TreeSet<String> set = new TreeSet<>();


    public void take(){

            lock.lock();
            try {

                if(queue.size() != 0){

                    long then = Long.valueOf(queue.peek().split(",")[0]) - System.currentTimeMillis();

                    if(then >= 0){

                        notNow.await(then, TimeUnit.MILLISECONDS);

                    }else {

                        notEnough.signalAll();


                    }

                }else {
                    notNow.await();
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }finally {
                lock.unlock();
            }

    }

    @Async("asyncServiceExecutor")
    public void add(String str){

        lock.lock();
        try {

            if(size() == 100){

                notEnough.await();
            }
            this.queue.add(str);

            notNow.signalAll();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

    }



}
