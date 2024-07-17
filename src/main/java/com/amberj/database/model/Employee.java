package com.amberj.database.model;


import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;

    private String role;

    @ColumnDefault(value = "18")
    private int age;

    public Employee() {
    }

    public Employee(String name, String role, int age) {
        this.name = name;
        this.role = role;
        this.age = age;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "Employee{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", role='" + role + '\'' +
            ", age='" + age + '\'' +
            '}';
    }
}
