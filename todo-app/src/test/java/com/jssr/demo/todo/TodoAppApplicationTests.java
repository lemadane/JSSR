package com.jssr.demo.todo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TodoAppApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void todoPageLoads() {
        ResponseEntity<String> response = restTemplate.getForEntity("/todos", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Todo Demo");
        assertThat(response.getBody()).contains("Spring MVC + JSSR record components.");
    }

    @Test
    void addAndToggleTodoFlowWorks() {
        String uniqueTitle = "todo-" + UUID.randomUUID();

        MultiValueMap<String, String> addForm = new LinkedMultiValueMap<>();
        addForm.add("title", uniqueTitle);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> addResponse = restTemplate.postForEntity(
            "/todos",
            new HttpEntity<>(addForm, headers),
            String.class
        );
        assertThat(addResponse.getStatusCode()).isIn(HttpStatus.FOUND, HttpStatus.OK);

        String query = URLEncoder.encode(uniqueTitle, StandardCharsets.UTF_8);
        ResponseEntity<String> listResponse = restTemplate.getForEntity("/todos?q=" + query, String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).contains(uniqueTitle);

        Matcher idMatcher = Pattern.compile("/todos/(\\d+)/toggle").matcher(listResponse.getBody());
        assertThat(idMatcher.find()).isTrue();
        String todoId = idMatcher.group(1);

        ResponseEntity<String> toggleResponse = restTemplate.postForEntity(
            "/todos/" + todoId + "/toggle",
            HttpEntity.EMPTY,
            String.class
        );
        assertThat(toggleResponse.getStatusCode()).isIn(HttpStatus.FOUND, HttpStatus.OK);

        ResponseEntity<String> toggledListResponse = restTemplate.getForEntity("/todos?q=" + query, String.class);
        assertThat(toggledListResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toggledListResponse.getBody()).contains(uniqueTitle);
        assertThat(toggledListResponse.getBody()).contains("Mark Open");
    }

    @Test
    void addAndDeleteTodoFlowWorks() {
        String uniqueTitle = "todo-delete-" + UUID.randomUUID();

        MultiValueMap<String, String> addForm = new LinkedMultiValueMap<>();
        addForm.add("title", uniqueTitle);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> addResponse = restTemplate.postForEntity(
            "/todos",
            new HttpEntity<>(addForm, headers),
            String.class
        );
        assertThat(addResponse.getStatusCode()).isIn(HttpStatus.FOUND, HttpStatus.OK);

        String query = URLEncoder.encode(uniqueTitle, StandardCharsets.UTF_8);
        ResponseEntity<String> listResponse = restTemplate.getForEntity("/todos?q=" + query, String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).contains(uniqueTitle);

        Matcher idMatcher = Pattern.compile("/todos/(\\d+)/delete").matcher(listResponse.getBody());
        assertThat(idMatcher.find()).isTrue();
        String todoId = idMatcher.group(1);

        ResponseEntity<String> deleteResponse = restTemplate.postForEntity(
            "/todos/" + todoId + "/delete",
            HttpEntity.EMPTY,
            String.class
        );
        assertThat(deleteResponse.getStatusCode()).isIn(HttpStatus.FOUND, HttpStatus.OK);

        ResponseEntity<String> afterDeleteResponse = restTemplate.getForEntity("/todos?q=" + query, String.class);
        assertThat(afterDeleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterDeleteResponse.getBody()).contains("No tasks yet. Add one above.");
        assertThat(afterDeleteResponse.getBody()).doesNotContain("/todos/" + todoId + "/delete");
    }

    @Test
    void fragmentEndpointsRenderPartialHtml() {
        String uniqueTitle = "fragment-" + UUID.randomUUID();

        MultiValueMap<String, String> addForm = new LinkedMultiValueMap<>();
        addForm.add("title", uniqueTitle);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        restTemplate.postForEntity(
            "/todos",
            new HttpEntity<>(addForm, headers),
            String.class
        );

        String query = URLEncoder.encode(uniqueTitle, StandardCharsets.UTF_8);
        ResponseEntity<String> listFragment = restTemplate.getForEntity("/todos/fragment/list?q=" + query, String.class);
        assertThat(listFragment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listFragment.getBody()).contains("<ul class=\"list\">");
        assertThat(listFragment.getBody()).contains(uniqueTitle);

        ResponseEntity<String> statsFragment = restTemplate.getForEntity("/todos/fragment/stats", String.class);
        assertThat(statsFragment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statsFragment.getBody()).contains("Completed:");

        ResponseEntity<String> listPartial = restTemplate.getForEntity("/todos/partial/list?q=" + query, String.class);
        assertThat(listPartial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listPartial.getBody()).contains("<ul class=\"list\">");
        assertThat(listPartial.getBody()).contains(uniqueTitle);

        ResponseEntity<String> statsPartial = restTemplate.getForEntity("/todos/partial/stats", String.class);
        assertThat(statsPartial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statsPartial.getBody()).contains("Completed:");
    }
}
