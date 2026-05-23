package com.example.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

  private final AtomicInteger counter = new AtomicInteger(0);
  private final Random random = new Random();
  private final List<String[]> quotes = new ArrayList<>();

  @PostConstruct
  public void loadQuotes() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    ClassPathResource resource = new ClassPathResource("quotes.json");
    JsonNode root = mapper.readTree(resource.getInputStream());
    for (JsonNode node : root.get("quotes")) {
      quotes.add(new String[] {node.get("quote").asText(), node.get("author").asText().trim()});
    }
  }

  @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
  public String index() {
    int count = counter.incrementAndGet();
    String[] quote = quotes.get(random.nextInt(quotes.size()));

    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Hello from Spring</title>
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
              min-height: 100vh;
              display: flex;
              align-items: center;
              justify-content: center;
              background: linear-gradient(135deg, #1a1a2e, #16213e, #0f3460);
              font-family: 'Segoe UI', sans-serif;
              color: #fff;
            }
            .card {
              background: rgba(255,255,255,0.08);
              backdrop-filter: blur(12px);
              border: 1px solid rgba(255,255,255,0.15);
              border-radius: 20px;
              padding: 48px 56px;
              max-width: 680px;
              text-align: center;
              box-shadow: 0 8px 32px rgba(0,0,0,0.4);
            }
            .emoji { font-size: 48px; margin-bottom: 16px; }
            h1 { font-size: 1.4rem; letter-spacing: 2px; text-transform: uppercase; color: #a78bfa; margin-bottom: 28px; }
            blockquote {
              font-size: 1.35rem;
              font-style: italic;
              line-height: 1.7;
              color: #f1f5f9;
              margin-bottom: 20px;
            }
            .author { font-size: 0.95rem; color: #94a3b8; letter-spacing: 1px; margin-bottom: 32px; }
            .counter {
              display: inline-block;
              background: rgba(167,139,250,0.2);
              border: 1px solid #a78bfa;
              border-radius: 999px;
              padding: 6px 20px;
              font-size: 0.85rem;
              color: #c4b5fd;
              letter-spacing: 1px;
            }
            .hint { margin-top: 16px; font-size: 0.78rem; color: #475569; }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="emoji">🌱</div>
            <h1>Hello from Spring Boot</h1>
            <blockquote>"%s"</blockquote>
            <p class="author">%s</p>
            <span class="counter">👁 Visit #%d</span>
            <p class="hint">Refresh for a new quote ↻</p>
          </div>
        </body>
        </html>
        """
        .formatted(quote[0], quote[1], count);
  }
}
