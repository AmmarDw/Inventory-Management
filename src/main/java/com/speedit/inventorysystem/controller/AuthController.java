package com.speedit.inventorysystem.controller;

import com.speedit.inventorysystem.enums.UserRoleEnum;
import com.speedit.inventorysystem.model.User;
import com.speedit.inventorysystem.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", UserRoleEnum.values());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("user") User user, BindingResult result, Model model) {

        // Custom manual validation (only for logic that @Valid doesn't cover)
        if (!user.getEmail().equals(user.getConfirmEmail())) {
            result.rejectValue("confirmEmail", null, "Emails do not match.");
        }

        if (!user.getPassword().equals(user.getConfirmPassword())) {
            result.rejectValue("confirmPassword", null, "Passwords do not match.");
        }

        if (userRepository.findByEmail(user.getEmail()) != null) {
            result.rejectValue("email", null, "An account with this email already exists.");
        }

        if (result.hasErrors()) {
            model.addAttribute("roles", UserRoleEnum.values());
            return "register";
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        return "redirect:/login";
    }
}
