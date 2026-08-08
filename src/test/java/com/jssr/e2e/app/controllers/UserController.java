package com.jssr.e2e.app.controllers;

import com.jssr.core.JssrComponent;
import com.jssr.e2e.app.components.PageLayout;
import com.jssr.e2e.app.components.UserForm;
import com.jssr.e2e.app.components.UserList;
import com.jssr.e2e.app.model.User;
import com.jssr.e2e.app.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@ResponseBody
@RequestMapping(produces = "text/html;charset=UTF-8")
public class UserController {

    private final UserRepository repository;

    public UserController(UserRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/")
    public JssrComponent index() {
        UserList listComponent = renderUserListComponent(repository.findAll(), "", null, null);
        return new PageLayout("User Management", listComponent.render());
    }

    @GetMapping("/users")
    public JssrComponent getUsers() {
        return renderUserListComponent(repository.findAll(), "", null, null);
    }

    @GetMapping("/users/new")
    public JssrComponent renderNewUserForm() {
        return new UserForm();
    }

    @GetMapping("/users/{id}/edit")
    public JssrComponent renderEditUserForm(@PathVariable("id") Long id) {
        Optional<User> userOpt = repository.findById(id);
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            return new UserForm(u.id(), u.name(), u.email(), u.role(), u.status(), true);
        }
        return new UserForm();
    }

    @GetMapping("/users/search")
    public JssrComponent searchUsers(@RequestParam(value = "q", required = false, defaultValue = "") String query) {
        List<User> searchResults = repository.search(query);
        return renderUserListComponent(searchResults, query, null, null);
    }

    @PostMapping("/users")
    public JssrComponent createUser(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "role", defaultValue = "Developer") String role,
            @RequestParam(value = "status", defaultValue = "ACTIVE") String status) {

        User newUser = new User(null, name, email, role, status, null);
        User saved = repository.save(newUser);

        return renderUserListComponent(
                repository.findAll(), 
                "", 
                "User '" + saved.name() + "' created successfully!", 
                "success"
        );
    }

    @PostMapping("/users/{id}")
    public JssrComponent updateUser(
            @PathVariable("id") Long id,
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("role") String role,
            @RequestParam("status") String status) {

        Optional<User> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            return renderUserListComponent(
                    repository.findAll(), 
                    "", 
                    "User not found!", 
                    "error"
            );
        }

        User updatedUser = new User(id, name, email, role, status, existingOpt.get().createdAt());
        repository.save(updatedUser);

        return renderUserListComponent(
                repository.findAll(), 
                "", 
                "User '" + updatedUser.name() + "' updated successfully!", 
                "success"
        );
    }

    @PostMapping("/users/{id}/toggle")
    public JssrComponent toggleUserStatus(@PathVariable("id") Long id) {
        Optional<User> existingOpt = repository.findById(id);
        if (existingOpt.isPresent()) {
            User u = existingOpt.get();
            String newStatus = u.isActive() ? "INACTIVE" : "ACTIVE";
            User updated = new User(u.id(), u.name(), u.email(), u.role(), newStatus, u.createdAt());
            repository.save(updated);

            return renderUserListComponent(
                    repository.findAll(), 
                    "", 
                    "Status for '" + u.name() + "' changed to " + newStatus, 
                    "info"
            );
        }

        return renderUserListComponent(repository.findAll(), "", "User not found!", "error");
    }

    @DeleteMapping("/users/{id}")
    public JssrComponent deleteUser(@PathVariable("id") Long id) {
        Optional<User> userOpt = repository.findById(id);
        String name = userOpt.map(User::name).orElse("User");
        
        repository.deleteById(id);

        return renderUserListComponent(
                repository.findAll(), 
                "", 
                "User '" + name + "' removed from system.", 
                "info"
        );
    }

    @GetMapping("/dashboard")
    public JssrComponent getDashboard(
            @RequestParam(value = "userType", required = false, defaultValue = "admin") String userType,
            @RequestParam(value = "fault", required = false, defaultValue = "false") boolean fault,
            @RequestParam(value = "emptyProjects", required = false, defaultValue = "false") boolean emptyProjects) {

        Object account = switch (userType.toLowerCase()) {
            case "admin" -> new com.jssr.e2e.app.model.AdminUser("Elena Rostova", "SUPERUSER,READ,WRITE");
            case "dev" -> new com.jssr.e2e.app.model.DeveloperUser("Marcus Vance", "@mvance", "Java");
            default -> new com.jssr.e2e.app.model.StandardUser("Sophia Chen", "ENTERPRISE");
        };

        List<com.jssr.e2e.app.components.SystemOverviewDashboardCard.ProjectInfo> projects = emptyProjects 
            ? List.of() 
            : List.of(
                new com.jssr.e2e.app.components.SystemOverviewDashboardCard.ProjectInfo("JSSR Core Engine", "Deploying", true, 10),
                new com.jssr.e2e.app.components.SystemOverviewDashboardCard.ProjectInfo("Internal Debug Helper", "Ignored", false, -1),
                new com.jssr.e2e.app.components.SystemOverviewDashboardCard.ProjectInfo("Spring WebMvc Integration", "Active", false, 20)
            );

        com.jssr.e2e.app.components.SystemOverviewDashboardCard card = 
            new com.jssr.e2e.app.components.SystemOverviewDashboardCard(account, projects, fault);
            
        return new PageLayout("Operations Control Center", card.render());
    }

    private UserList renderUserListComponent(List<User> users, String query, String toastMsg, String toastType) {
        long total = repository.countTotal();
        long active = repository.countActive();
        long admins = repository.countAdmins();
        return new UserList(users, total, active, admins, query, toastMsg, toastType);
    }
}
