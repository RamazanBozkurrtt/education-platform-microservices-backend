package com.edubase.user.controller;

import com.edubase.user.controller.base.RestBaseController;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.service.abstracts.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController extends RestBaseController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getAll(@RequestParam(defaultValue = "0") int pageNumber,
                                    @RequestParam(defaultValue = "10") int pageSize) {

        return ok(userService.getAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {

        return ok(userService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UserProfile userProfile) {
        return ok(userService.update(id, userProfile));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        userService.delete(id);
        return noContent();
    }
}
