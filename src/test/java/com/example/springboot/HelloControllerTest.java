package com.example.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HelloControllerTest {

  private HelloController controller;

  @BeforeEach
  void setUp() throws Exception {
    controller = new HelloController();
    controller.loadQuotes();
  }

  @Test
  void indexReturnsHtml() {
    String response = controller.index();
    assertThat(response).contains("<!DOCTYPE html>");
  }

  @Test
  void indexContainsVisitCounter() {
    String response = controller.index();
    assertThat(response).contains("Visit #1");
  }

  @Test
  void counterIncrementsOnEachCall() {
    controller.index();
    controller.index();
    String third = controller.index();
    assertThat(third).contains("Visit #3");
  }

  @Test
  void indexContainsBlockquote() {
    String response = controller.index();
    assertThat(response).contains("<blockquote>");
  }

  @Test
  void indexContainsAuthor() {
    String response = controller.index();
    assertThat(response).contains("class=\"author\"");
  }

  @Test
  void indexContainsTitle() {
    String response = controller.index();
    assertThat(response).contains("Hello from Spring Boot");
  }
}
