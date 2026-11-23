package com.stecker.backend.controller;

import com.stecker.backend.dto.MenuItemResponse;
import com.stecker.backend.service.MenuService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * How long a browser may reuse the menu. (The frontend's nginx keeps
     * it for 60 seconds on its own, see frontend/nginx/default.conf.template,
     * so a rush of guests costs the database about one query per nginx copy
     * and minute.)
     * A changed menu reaches guests within about 1.5 minutes.
     */
    static final Duration CACHE_FOR = Duration.ofSeconds(30);

    /** All products, ordered by id, including unavailable ones. */
    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> getMenu() {
        List<MenuItemResponse> menu = menuService.getMenuItems()
                .stream()
                .map(MenuItemResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_FOR).cachePublic())
                .body(menu);
    }
}
