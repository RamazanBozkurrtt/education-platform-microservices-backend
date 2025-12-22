package com.edubase.auth.entity;


import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "role")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Role {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "role_seq")
    @SequenceGenerator(name = "role_seq", sequenceName = "role_seq", allocationSize = 1)
    @Column(name = "role_id")
    Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "user_roles")
    @ToString.Exclude
    private Set<User> users = new HashSet<>();

    public String  toString(){
        return name;
    }

}
