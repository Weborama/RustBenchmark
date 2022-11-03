package com.example.javaspringreactive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ove.crypto.digest.Blake2b;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.sql.Types;

@SpringBootApplication
@RestController
public class JavaSpringReactiveApplication {

  private final Blake2b blake2b = Blake2b.Digest.newInstance();
  private final RabbitTemplate template;
  private final ObjectMapper mapper;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  public JavaSpringReactiveApplication(RabbitTemplate template, ObjectMapper mapper, JdbcTemplate jdbcTemplate) {
    this.template = template;
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
  }

  private String fetchName(Input input) {
    return jdbcTemplate.queryForObject("SELECT name FROM clients WHERE id = ?", new Object[] {input.id()},
        new int[] {Types.INTEGER}, (rs, i) -> rs.getString("name"));
  }

  @PostMapping("/hash")
  public Mono<Output> hash(@RequestBody Input input) {
    return Mono.just(input)
        .map(this::fetchName)
        .map(clientName -> new Output(clientName, blake2b.digest(input.data().getBytes(StandardCharsets.UTF_8))))
        .doOnNext(o -> {
          try {
            template.convertAndSend("toRustDemo", "rust", mapper.writeValueAsBytes(o));
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        });
  }


  public static void main(String[] args) {
    SpringApplication.run(JavaSpringReactiveApplication.class, args);
  }



  public record Input(int id, String data) {
  }


  public record Output(String name, byte[] hash) {
  }
}
