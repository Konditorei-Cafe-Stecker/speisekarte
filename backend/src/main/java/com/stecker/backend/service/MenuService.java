package com.stecker.backend.service;

import com.stecker.backend.model.MenuItem;
import com.stecker.backend.repository.MenuRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    public List<MenuItem> getMenuItems() {
        return menuRepository.findAllByOrderByIdAsc();
    }
}