package sga_backend.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sga_backend.entity.TypeConge;
import sga_backend.service.TypeCongeService;

@RestController
@RequestMapping("/api/types-conge")
public class TypeCongeController {

    @Autowired private TypeCongeService typeCongeService;

    // GET /api/types-conge — types actifs (pour le formulaire de demande)
    @GetMapping
    public List<TypeConge> getTypesActifs() {
        return typeCongeService.getTypesActifs();
    }

    // GET /api/types-conge/tous — tous les types (admin)
    @GetMapping("/tous")
    public List<TypeConge> getTous() {
        return typeCongeService.getTous();
    }
}