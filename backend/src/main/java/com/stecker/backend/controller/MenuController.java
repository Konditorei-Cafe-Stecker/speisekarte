package com.stecker.backend.controller;
import com.stecker.backend.model.MenuItem;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import com.stecker.backend.service.MenuService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }
    @GetMapping
    public List<MenuItem> getMenu() {
        return menuService.getMenuItems();
    }
}