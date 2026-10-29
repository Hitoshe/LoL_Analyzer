package com.lol.analyzer.controller;

import com.lol.analyzer.model.AnalysisRequest;
import com.lol.analyzer.model.ChampionInfo;
import com.lol.analyzer.model.Lane;
import com.lol.analyzer.riot.Region;
import com.lol.analyzer.service.ChampionCatalog;
import com.lol.analyzer.service.DataDragonService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the roster form at {@code /} and fills the reference data it needs (regions, lanes, champions with icons).
 */
@Controller
public class WebController {

    private final DataDragonService dataDragonService;
    private final Region defaultRegion;

    public WebController(DataDragonService dataDragonService, Region defaultRegion) {
        this.dataDragonService = dataDragonService;
        this.defaultRegion = defaultRegion;
    }

    @GetMapping("/")
    public String index(Model model) {
        return renderForm(model, AnalysisRequest.blank(defaultRegion.name()), List.of());
    }

    public String renderForm(Model model, AnalysisRequest form, List<String> errors) {
        form.padRows();
        if (Region.parse(form.getRegion()).isEmpty()) {
            form.setRegion(defaultRegion.name());
        }
        ChampionCatalog catalog = dataDragonService.catalog();
        List<ChampionInfo> champions = catalog.sortedByName();
        Map<String, String> icons = new LinkedHashMap<>();
        champions.forEach(c -> icons.put(c.name(), c.iconUrl()));

        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("regions", Region.values());
        model.addAttribute("lanes", Lane.values());
        model.addAttribute("champions", champions);
        model.addAttribute("championIcons", icons);
        model.addAttribute("patch", catalog.patch());
        return "index";
    }
}
