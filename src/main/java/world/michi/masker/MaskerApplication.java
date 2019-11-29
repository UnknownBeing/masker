package world.michi.masker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @ClassName MaskerApplication
 * @Author 17
 * @Date 2019/11/26 14:57
 * @Description ...
 **/


@SpringBootApplication
@EnableAsync
public class MaskerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaskerApplication.class, args);
    }


    @Bean
    public Executor asyncServiceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(8);

        executor.setMaxPoolSize(32);

        executor.setQueueCapacity(10000);

        executor.setThreadNamePrefix("masker-");

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }

}
