package com.jssr.demo.todo;

import com.jssr.spring.JssrMvcConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(JssrMvcConfig.class)
public class TodoAppApplication {

   public static void main(String[] args) {
      SpringApplication.run(
            TodoAppApplication.class,
            args);
   }
}
