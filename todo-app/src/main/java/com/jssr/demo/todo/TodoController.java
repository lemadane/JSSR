package com.jssr.demo.todo;

import com.jssr.core.JssrComponent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(produces = MediaType.TEXT_HTML_VALUE)
public class TodoController {

   private final AtomicLong ids = new AtomicLong();
   private final List<Todo> todos = new CopyOnWriteArrayList<>();

   @GetMapping({ "/", "/todos" })
   @ResponseBody
   public JssrComponent list(@RequestParam(name = "q", defaultValue = "") String query) {
      String normalizedQuery = normalizeQuery(query);
      List<Todo> visibleTodos = filterTodos(normalizedQuery);

      return new TodoPage(visibleTodos, todos.size(), countCompleted(), normalizedQuery);
   }

   @GetMapping("/todos/fragment/list")
   @ResponseBody
   public JssrComponent listFragment(@RequestParam(name = "q", defaultValue = "") String query) {
      String normalizedQuery = normalizeQuery(query);
      return new TodoList(filterTodos(normalizedQuery));
   }

   @GetMapping("/todos/partial/list")
   @ResponseBody
   public JssrComponent listPartial(@RequestParam(name = "q", defaultValue = "") String query) {
      return listFragment(query);
   }

   @GetMapping("/todos/fragment/stats")
   @ResponseBody
   public JssrComponent statsFragment() {
      return new TodoStats(countCompleted(), todos.size());
   }

   @GetMapping("/todos/partial/stats")
   @ResponseBody
   public JssrComponent statsPartial() {
      return statsFragment();
   }

   @PostMapping(path = "/todos", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
   public String add(@RequestParam("title") String title) {
      String normalizedTitle = title == null ? "" : title.trim();
      if (!normalizedTitle.isBlank()) {
         todos.add(new Todo(ids.incrementAndGet(), normalizedTitle));
      }
      return "redirect:/todos";
   }

   @PostMapping("/todos/{id}/toggle")
   public String toggle(@PathVariable("id") long id) {
      for (int i = 0; i < todos.size(); i++) {
         Todo current = todos.get(i);
         if (current.id() == id) {
            todos.set(i, current.toggle());
            break;
         }
      }
      return "redirect:/todos";
   }

   @PostMapping(path = "/todos/{id}/edit", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
   public String edit(@PathVariable("id") long id, @RequestParam("title") String title) {
      String normalizedTitle = title == null ? "" : title.trim();
      if (normalizedTitle.isBlank()) {
         return "redirect:/todos";
      }

      for (int i = 0; i < todos.size(); i++) {
         Todo current = todos.get(i);
         if (current.id() == id) {
            todos.set(i, current.withTitle(normalizedTitle));
            break;
         }
      }
      return "redirect:/todos";
   }

   @PostMapping("/todos/{id}/delete")
   public String delete(@PathVariable("id") long id) {
      todos.removeIf(todo -> todo.id() == id);
      return "redirect:/todos";
   }

   private String normalizeQuery(String query) {
      return query == null ? "" : query.trim();
   }

   private List<Todo> filterTodos(String normalizedQuery) {
      String lowercaseQuery = normalizedQuery.toLowerCase(Locale.ROOT);
      return todos.stream()
            .filter(todo -> lowercaseQuery.isEmpty() || todo.title().toLowerCase(Locale.ROOT).contains(lowercaseQuery))
            .toList();
   }

   private long countCompleted() {
      return todos.stream().filter(Todo::completed).count();
   }
}
