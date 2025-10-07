package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long githubId;

    @Column(unique = true, nullable = false)
    private String githubUsername;

    @Column(nullable = true)
    private String email;

    @Column(nullable = true) // <-- NUEVO
    private String name;

    @Column(nullable = true) // <-- NUEVO
    private String avatarUrl;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public User() {
    }

    public User(Long githubId, String githubUsername, String email) {
        this.githubId = githubId;
        this.githubUsername = githubUsername;
        this.email = email;
        this.name = null;
        this.avatarUrl = null;
        this.active = true;
    }

    public User(Long githubId, String githubUsername, String email, String name, String avatarUrl) { // <-- ACTUALIZADO
        this.githubId = githubId;
        this.githubUsername = githubUsername;
        this.email = email;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.active = true;
    }

    // ... getters y setters para los campos existentes ...

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    // ... el resto de getters, setters, equals y hashCode sin cambios ...
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGithubId() {
        return githubId;
    }

    public void setGithubId(Long githubId) {
        this.githubId = githubId;
    }

    public String getGithubUsername() {
        return githubUsername;
    }



    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(githubId, user.githubId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(githubId);
    }
}
