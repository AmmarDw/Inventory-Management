package com.speedit.inventorysystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.speedit.inventorysystem.enums.UserRoleEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity{

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO,generator="native")
    @GenericGenerator(name = "native",strategy = "native")
    private int userId;

    @Enumerated(EnumType.STRING)
    private UserRoleEnum role;

    @NotBlank(message="Name must not be blank")
    @Size(min=3, message="Name must be at least 3 characters long")
    private String name;

    @NotBlank(message="Mobile number must not be blank")
    @Pattern(regexp="(^$|[0-9]{10})",message = "Mobile number must be 10 digits")
    private String phoneNumber;

    @NotBlank(message="Email must not be blank")
    @Email(message = "Please provide a valid email address" )
    private String email;

    @NotBlank(message="Confirm Email must not be blank")
    @Email(message = "Please provide a valid confirm email address")
    @Transient
    @JsonIgnore
    private String confirmEmail;

    @NotBlank(message="Password must not be blank")
    @Size(min=8, message="Password must be at least 8 characters long")
    @JsonIgnore
    private String password;

    @NotBlank(message="Confirm Password must not be blank")
    @Size(min=8, message="Confirm Password must be at least 8 characters long")
    @Transient
    @JsonIgnore
    private String confirmPassword;

    @ManyToMany(mappedBy = "users")
    private List<Notification> notifications = new ArrayList<>();

}