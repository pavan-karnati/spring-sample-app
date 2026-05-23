package com.example.springboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelloControllerIntegrationTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeAll
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void rootEndpointReturns200() throws Exception {
    mockMvc.perform(get("/")).andExpect(status().isOk());
  }

  @Test
  void rootEndpointReturnsHtml() throws Exception {
    mockMvc.perform(get("/")).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void responseContainsExpectedHtmlElements() throws Exception {
    MvcResult result = mockMvc.perform(get("/")).andReturn();
    String body = result.getResponse().getContentAsString();

    assertThat(body).contains("<!DOCTYPE html>");
    assertThat(body).contains("Hello from Spring Boot");
    assertThat(body).contains("<blockquote>");
    assertThat(body).contains("Visit #");
  }

  @Test
  void counterIncrementsAcrossRequests() throws Exception {
    MvcResult first = mockMvc.perform(get("/")).andReturn();
    MvcResult second = mockMvc.perform(get("/")).andReturn();

    String firstVisit = extractVisitLine(first.getResponse().getContentAsString());
    String secondVisit = extractVisitLine(second.getResponse().getContentAsString());

    assertThat(firstVisit).isNotEqualTo(secondVisit);
  }

  private String extractVisitLine(String body) {
    return body.lines().filter(l -> l.contains("Visit #")).findFirst().orElse("");
  }
}
