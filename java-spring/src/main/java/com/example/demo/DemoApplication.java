package com.example.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ove.crypto.digest.Blake2b;

import java.nio.charset.StandardCharsets;
import java.sql.Types;

@SpringBootApplication
@RestController
@Slf4j
public class DemoApplication {

  private final Blake2b blake2b = Blake2b.Digest.newInstance();

  private final JdbcTemplate template;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper mapper;

  @Autowired
  public DemoApplication(JdbcTemplate template, RabbitTemplate rabbitTemplate, ObjectMapper mapper) {
    this.template = template;
    this.rabbitTemplate = rabbitTemplate;
    this.mapper = mapper;
  }

  @PostMapping(value = "/hash", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Output> hash(@RequestBody Input input) throws JsonProcessingException {

    String name;
    try {
      name = template.queryForObject("SELECT name from clients where id = ?", new Object[] {input.id()},
          new int[] {Types.INTEGER}, (rs, rowNum) -> rs.getString("name"));
    } catch (EmptyResultDataAccessException ex) {
      return ResponseEntity.notFound().build();
    }
    Output message = new Output(name, blake2b.digest(input.data().getBytes(StandardCharsets.UTF_8)));
    rabbitTemplate.convertAndSend("toRustDemo", "rust",
        mapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8));
    return ResponseEntity.ok(message);
  }


  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }


  public record Input(int id, String data) {
  }


  public record Output(String name, byte[] hash) {
  }

}
